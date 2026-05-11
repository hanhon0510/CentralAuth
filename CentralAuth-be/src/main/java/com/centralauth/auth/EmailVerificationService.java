package com.centralauth.auth;

import java.security.SecureRandom;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final String KEY_PREFIX = "email-verification:";
	private static final int OTP_BOUND = 1_000_000;

	private final StringRedisTemplate redisTemplate;
	private final SecureRandom secureRandom;
	private final Duration otpTtl;

	public EmailVerificationService(
			StringRedisTemplate redisTemplate,
			@Value("${centralauth.email-verification.otp-ttl:10m}") Duration otpTtl) {
		this.redisTemplate = redisTemplate;
		this.secureRandom = new SecureRandom();
		this.otpTtl = otpTtl;
	}

	public void issueOtp(String email) {
		String otp = generateOtp();
		redisTemplate.opsForValue().set(redisKey(email), otp, otpTtl);
		log.info("Email verification OTP for {}: {}", email, otp);
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
}
