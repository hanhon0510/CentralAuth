package com.centralauth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenRevocationService {

	private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
	private static final String USER_LOGOUT_AFTER_KEY_PREFIX = "jwt:user-logout-after:";
	private static final String HASH_PREFIX = "sha256:";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final StringRedisTemplate redisTemplate;
	private final long accessTokenExpiresInSeconds;

	public AccessTokenRevocationService(
			StringRedisTemplate redisTemplate,
			@Value("${centralauth.jwt.expires-in-seconds:3600}") long accessTokenExpiresInSeconds) {
		this.redisTemplate = redisTemplate;
		this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
	}

	public void revokeToken(String token, long expiresAtEpochSecond) {
		if (token == null || token.isBlank()) {
			return;
		}
		long ttlSeconds = expiresAtEpochSecond - Instant.now().getEpochSecond();
		if (ttlSeconds <= 0) {
			return;
		}
		redisTemplate.opsForValue().set(blacklistKey(token), "1", Duration.ofSeconds(ttlSeconds));
	}

	public void revokeTokensIssuedAtOrBefore(String userId, Instant cutoff) {
		if (userId == null || userId.isBlank()) {
			return;
		}
		redisTemplate.opsForValue().set(
				userLogoutAfterKey(userId),
				Long.toString(cutoff.getEpochSecond()),
				Duration.ofSeconds(accessTokenExpiresInSeconds));
	}

	public boolean isRevoked(String token, JwtPrincipal principal) {
		if (token == null || token.isBlank()) {
			return false;
		}
		if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(token)))) {
			return true;
		}

		String logoutAfter = redisTemplate.opsForValue().get(userLogoutAfterKey(principal.userId()));
		if (logoutAfter == null || logoutAfter.isBlank()) {
			return false;
		}
		try {
			return principal.issuedAtEpochSecond() <= Long.parseLong(logoutAfter);
		}
		catch (NumberFormatException ex) {
			return true;
		}
	}

	private String blacklistKey(String token) {
		return BLACKLIST_KEY_PREFIX + hashToken(token);
	}

	private String userLogoutAfterKey(String userId) {
		return USER_LOGOUT_AFTER_KEY_PREFIX + userId;
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HASH_PREFIX + BASE64_URL_ENCODER.encodeToString(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
