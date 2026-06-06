package com.centralauth.auth.login;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

	private static final String LOCK_VALUE = "1";
	private static final long REDIS_KEY_WITHOUT_EXPIRY = -1;
	private static final long REDIS_KEY_NOT_FOUND = -2;
	@NonNull
	private static final Duration DEFAULT_FAILURE_WINDOW = Objects.requireNonNull(Duration.ofMinutes(15));
	@NonNull
	private static final Duration DEFAULT_LOCK_DURATION = Objects.requireNonNull(Duration.ofMinutes(15));
	@NonNull
	private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Objects.requireNonNull(Duration.ofMinutes(1));

	private final StringRedisTemplate redisTemplate;
	private final int maxFailedAttempts;
	private final int maxAttemptsPerWindow;
	@NonNull
	private final Duration failureWindow;
	@NonNull
	private final Duration lockDuration;
	@NonNull
	private final Duration rateLimitWindow;
	private final int lockDurationSeconds;
	private final int rateLimitWindowSeconds;

	public LoginAttemptService(
			StringRedisTemplate redisTemplate,
			@Value("${centralauth.login-protection.max-failed-attempts:5}") int maxFailedAttempts,
			@Value("${centralauth.login-protection.failure-window:15m}") Duration failureWindow,
			@Value("${centralauth.login-protection.lock-duration:15m}") Duration lockDuration,
			@Value("${centralauth.login-protection.rate-limit.max-attempts:10}") int maxAttemptsPerWindow,
			@Value("${centralauth.login-protection.rate-limit.window:1m}") Duration rateLimitWindow) {
		this.redisTemplate = redisTemplate;
		this.maxFailedAttempts = Math.max(1, maxFailedAttempts);
		this.maxAttemptsPerWindow = Math.max(1, maxAttemptsPerWindow);
		this.failureWindow = positiveOrDefault(failureWindow, DEFAULT_FAILURE_WINDOW);
		this.lockDuration = positiveOrDefault(lockDuration, DEFAULT_LOCK_DURATION);
		this.rateLimitWindow = positiveOrDefault(rateLimitWindow, DEFAULT_RATE_LIMIT_WINDOW);
		this.lockDurationSeconds = durationSeconds(this.lockDuration);
		this.rateLimitWindowSeconds = durationSeconds(this.rateLimitWindow);
	}

	public void recordAttempt(@NonNull String email, @NonNull String clientIp) {
		int retryAfterSeconds = Math.max(
				recordRateLimitAttempt(LoginAttemptScope.EMAIL.rateKey(email)),
				recordRateLimitAttempt(LoginAttemptScope.IP.rateKey(clientIp)));
		if (retryAfterSeconds > 0) {
			throw new LoginRateLimitExceededException(retryAfterSeconds);
		}
	}

	public void requireLoginAllowed(@NonNull String email, @NonNull String clientIp) {
		int retryAfterSeconds = Math.max(
				remainingLockSeconds(LoginAttemptScope.EMAIL.lockKey(email)),
				remainingLockSeconds(LoginAttemptScope.IP.lockKey(clientIp)));
		if (retryAfterSeconds > 0) {
			throw new LoginTemporarilyLockedException(retryAfterSeconds);
		}
	}

	public void recordFailure(@NonNull String email, @NonNull String clientIp) {
		recordFailureKey(
				LoginAttemptScope.EMAIL.failureKey(email),
				LoginAttemptScope.EMAIL.lockKey(email));
		recordFailureKey(
				LoginAttemptScope.IP.failureKey(clientIp),
				LoginAttemptScope.IP.lockKey(clientIp));
	}

	public void recordSuccess(@NonNull String email, @NonNull String clientIp) {
		redisTemplate.delete(List.of(
				LoginAttemptScope.EMAIL.failureKey(email),
				LoginAttemptScope.IP.failureKey(clientIp)));
	}

	private void recordFailureKey(@NonNull String failureKey, @NonNull String lockKey) {
		Long failedAttempts = redisTemplate.opsForValue().increment(failureKey);
		if (failedAttempts == null) {
			return;
		}
		if (failedAttempts == 1) {
			redisTemplate.expire(failureKey, failureWindow);
		}
		if (failedAttempts >= maxFailedAttempts) {
			redisTemplate.opsForValue().set(lockKey, LOCK_VALUE, lockDuration);
		}
	}

	private int recordRateLimitAttempt(@NonNull String rateKey) {
		Long attempts = redisTemplate.opsForValue().increment(rateKey);
		if (attempts == null) {
			return 0;
		}
		if (attempts == 1) {
			redisTemplate.expire(rateKey, rateLimitWindow);
		}
		if (attempts > maxAttemptsPerWindow) {
			return remainingSeconds(rateKey, rateLimitWindowSeconds);
		}
		return 0;
	}

	private int remainingLockSeconds(@NonNull String lockKey) {
		Long remainingSeconds = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
		if (remainingSeconds == null || remainingSeconds == REDIS_KEY_NOT_FOUND || remainingSeconds == 0) {
			return 0;
		}
		if (remainingSeconds == REDIS_KEY_WITHOUT_EXPIRY) {
			return lockDurationSeconds;
		}
		if (remainingSeconds < 0) {
			return 0;
		}
		return Math.toIntExact(Math.min(remainingSeconds, Integer.MAX_VALUE));
	}

	private int remainingSeconds(@NonNull String key, int fallbackSeconds) {
		Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
		if (remainingSeconds == null || remainingSeconds <= 0) {
			return fallbackSeconds;
		}
		return Math.toIntExact(Math.min(remainingSeconds, Integer.MAX_VALUE));
	}

	@NonNull
	private static Duration positiveOrDefault(Duration duration, @NonNull Duration defaultDuration) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			return defaultDuration;
		}
		return duration;
	}

	private static int durationSeconds(Duration duration) {
		long seconds = Math.max(1, duration.toSeconds());
		return Math.toIntExact(Math.min(seconds, Integer.MAX_VALUE));
	}

}
