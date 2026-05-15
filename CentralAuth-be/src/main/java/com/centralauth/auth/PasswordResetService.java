package com.centralauth.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final int RESET_TOKEN_BYTES = 32;
	private static final String KEY_PREFIX = "password-reset:";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final StringRedisTemplate redisTemplate;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Duration tokenTtl;

	public PasswordResetService(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService,
			StringRedisTemplate redisTemplate,
			@Value("${centralauth.password-reset.token-ttl:15m}") Duration tokenTtl) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.redisTemplate = redisTemplate;
		this.tokenTtl = tokenTtl;
	}

	public void requestReset(String email) {
		String normalizedEmail = normalizeEmail(email);
		userMapper.findByEmail(normalizedEmail)
				.filter(User::enabled)
				.ifPresent(this::issueResetToken);
	}

	@Transactional
	public void resetPassword(String token, String newPassword) {
		String normalizedToken = token.trim();
		String userId = redisTemplate.opsForValue().getAndDelete(redisKey(normalizedToken));
		if (userId == null || userId.isBlank()) {
			throw new InvalidPasswordResetTokenException();
		}
		int updated = userMapper.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
		if (updated == 0) {
			throw new InvalidPasswordResetTokenException();
		}
		refreshTokenService.revokeAllActiveRefreshTokens(userId);
	}

	private void issueResetToken(User user) {
		String token = createOpaqueToken();
		redisTemplate.opsForValue().set(redisKey(token), user.id(), tokenTtl);
		log.info("Password reset token for {}: {}", user.email(), token);
	}

	private String createOpaqueToken() {
		byte[] bytes = new byte[RESET_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return BASE64_URL_ENCODER.encodeToString(bytes);
	}

	private String redisKey(String token) {
		return KEY_PREFIX + token;
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
