package com.centralauth.auth.verification;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.centralauth.auth.exception.EmailVerificationOtpResendThrottledException;
import com.centralauth.auth.exception.InvalidEmailVerificationOtpException;

@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final String KEY_PREFIX = "email-verification:";
	private static final String RESEND_THROTTLE_KEY_PREFIX = "email-verification-resend:";
	private static final int OTP_BOUND = 1_000_000;

	private final StringRedisTemplate redisTemplate;
	private final SecureRandom secureRandom;
	private final Duration otpTtl;
	private final Duration resendCooldown;

	public EmailVerificationService(
			StringRedisTemplate redisTemplate,
			@Value("${centralauth.email-verification.otp-ttl:10m}") Duration otpTtl,
			@Value("${centralauth.email-verification.resend-cooldown:60s}") Duration resendCooldown) {
		this.redisTemplate = redisTemplate;
		this.secureRandom = new SecureRandom();
		this.otpTtl = otpTtl;
		this.resendCooldown = resendCooldown;
	}

	public void issueOtp(String email) {
		String otp = generateOtp();
		redisTemplate.opsForValue().set(redisKey(email), otp, otpTtl);
		log.info("Email verification OTP for {}: {}", email, otp);
	}

	public int resendOtp(String email) {
		int cooldownSeconds = resendCooldownSeconds();
		String throttleKey = resendThrottleKey(email);
		Boolean cooldownStarted = redisTemplate.opsForValue().setIfAbsent(throttleKey, "1", resendCooldown);
		if (!Boolean.TRUE.equals(cooldownStarted)) {
			throw new EmailVerificationOtpResendThrottledException(remainingCooldownSeconds(throttleKey, cooldownSeconds));
		}
		issueOtp(email);
		return cooldownSeconds;
	}

	public void requireValidOtp(String email, String otp) {
		String redisKey = redisKey(email);
		String expectedOtp = redisTemplate.opsForValue().get(redisKey);
		if (!otp.equals(expectedOtp)) {
			throw new InvalidEmailVerificationOtpException();
		}
	}

	public void consumeOtp(String email) {
		String redisKey = redisKey(email);
		redisTemplate.delete(redisKey);
	}

	private String generateOtp() {
		return "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
	}

	private String redisKey(String email) {
		return KEY_PREFIX + email;
	}

	private String resendThrottleKey(String email) {
		return RESEND_THROTTLE_KEY_PREFIX + email;
	}

	private int resendCooldownSeconds() {
		long seconds = Math.max(1, resendCooldown.toSeconds());
		return Math.toIntExact(Math.min(seconds, Integer.MAX_VALUE));
	}

	private int remainingCooldownSeconds(String throttleKey, int fallbackSeconds) {
		Long remainingSeconds = redisTemplate.getExpire(throttleKey, TimeUnit.SECONDS);
		if (remainingSeconds == null || remainingSeconds <= 0) {
			return fallbackSeconds;
		}
		return Math.toIntExact(Math.min(remainingSeconds, Integer.MAX_VALUE));
	}
}
