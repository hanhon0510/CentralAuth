package com.centralauth.auth.password;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.auth.exception.InvalidPasswordResetTokenException;
import com.centralauth.auth.token.RefreshTokenService;
import com.centralauth.event.auth.PasswordChangedEvent;
import com.centralauth.event.auth.PasswordResetRequestedEvent;
import com.centralauth.user.AccountStatus;
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
	private final ApplicationEventPublisher eventPublisher;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Duration tokenTtl;

	public PasswordResetService(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService,
			StringRedisTemplate redisTemplate,
			ApplicationEventPublisher eventPublisher,
			@Value("${centralauth.password-reset.token-ttl:15m}") Duration tokenTtl) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.redisTemplate = redisTemplate;
		this.eventPublisher = eventPublisher;
		this.tokenTtl = tokenTtl;
	}

	@Transactional
	public void requestReset(String email) {
		String normalizedEmail = normalizeEmail(email);
		userMapper.findByEmail(normalizedEmail)
				.filter(user -> user.accountStatus() == AccountStatus.ACTIVE)
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
		User user = userMapper.findById(userId).orElseThrow(InvalidPasswordResetTokenException::new);
		eventPublisher.publishEvent(new PasswordChangedEvent(user.id(), user.email(), Instant.now()));
	}

	private void issueResetToken(User user) {
		String token = createOpaqueToken();
		redisTemplate.opsForValue().set(redisKey(token), user.id(), tokenTtl);
		log.info("Password reset token for {}: {}", user.email(), token);
		eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.id(), user.email(), Instant.now()));
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
