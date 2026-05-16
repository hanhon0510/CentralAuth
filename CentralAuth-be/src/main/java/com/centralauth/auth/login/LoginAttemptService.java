package com.centralauth.auth.login;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

	private static final String FAILURE_EMAIL_KEY_PREFIX = "login-failure:email:";
	private static final String FAILURE_IP_KEY_PREFIX = "login-failure:ip:";
	private static final String RATE_EMAIL_KEY_PREFIX = "login-rate:email:";
	private static final String RATE_IP_KEY_PREFIX = "login-rate:ip:";
	private static final String LOCK_EMAIL_KEY_PREFIX = "login-lock:email:";
	private static final String LOCK_IP_KEY_PREFIX = "login-lock:ip:";
	private static final String LOCK_VALUE = "1";
	private static final Duration DEFAULT_FAILURE_WINDOW = Duration.ofMinutes(15);
	private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(15);
	private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

	private final StringRedisTemplate redisTemplate;
	private final int maxFailedAttempts;
	private final int maxAttemptsPerWindow;
	private final Duration failureWindow;
	private final Duration lockDuration;
	private final Duration rateLimitWindow;

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
	}

	public void recordAttempt(String email, String clientIp) {
		int retryAfterSeconds = Math.max(
				recordRateLimitAttempt(emailRateKey(email)),
				recordRateLimitAttempt(ipRateKey(clientIp)));
		if (retryAfterSeconds > 0) {
			throw new LoginRateLimitExceededException(retryAfterSeconds);
		}
	}

	public void requireLoginAllowed(String email, String clientIp) {
		int retryAfterSeconds = Math.max(
				remainingLockSeconds(emailLockKey(email)),
				remainingLockSeconds(ipLockKey(clientIp)));
		if (retryAfterSeconds > 0) {
			throw new LoginTemporarilyLockedException(retryAfterSeconds);
		}
	}

	public void recordFailure(String email, String clientIp) {
		recordFailureKey(emailFailureKey(email), emailLockKey(email));
		recordFailureKey(ipFailureKey(clientIp), ipLockKey(clientIp));
	}

	public void recordSuccess(String email, String clientIp) {
		redisTemplate.delete(List.of(emailFailureKey(email), ipFailureKey(clientIp)));
	}

	private void recordFailureKey(String failureKey, String lockKey) {
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

	private int recordRateLimitAttempt(String rateKey) {
		Long attempts = redisTemplate.opsForValue().increment(rateKey);
		if (attempts == null) {
			return 0;
		}
		if (attempts == 1) {
			redisTemplate.expire(rateKey, rateLimitWindow);
		}
		if (attempts > maxAttemptsPerWindow) {
			return remainingSeconds(rateKey, rateLimitWindowSeconds());
		}
		return 0;
	}

	private int remainingLockSeconds(String lockKey) {
		return remainingSeconds(lockKey, 0);
	}

	private int remainingSeconds(String key, int fallbackSeconds) {
		Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
		if (remainingSeconds == null || remainingSeconds <= 0) {
			return fallbackSeconds;
		}
		return Math.toIntExact(Math.min(remainingSeconds, Integer.MAX_VALUE));
	}

	private String emailFailureKey(String email) {
		return FAILURE_EMAIL_KEY_PREFIX + email;
	}

	private String ipFailureKey(String clientIp) {
		return FAILURE_IP_KEY_PREFIX + clientIp;
	}

	private String emailRateKey(String email) {
		return RATE_EMAIL_KEY_PREFIX + email;
	}

	private String ipRateKey(String clientIp) {
		return RATE_IP_KEY_PREFIX + clientIp;
	}

	private String emailLockKey(String email) {
		return LOCK_EMAIL_KEY_PREFIX + email;
	}

	private String ipLockKey(String clientIp) {
		return LOCK_IP_KEY_PREFIX + clientIp;
	}

	private Duration positiveOrDefault(Duration duration, Duration defaultDuration) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			return defaultDuration;
		}
		return duration;
	}

	private int rateLimitWindowSeconds() {
		long seconds = Math.max(1, rateLimitWindow.toSeconds());
		return Math.toIntExact(Math.min(seconds, Integer.MAX_VALUE));
	}
}
