package com.centralauth.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

	private static final int REFRESH_TOKEN_BYTES = 32;
	private static final String HASH_PREFIX = "sha256:";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final RefreshTokenMapper refreshTokenMapper;
	private final SecureRandom secureRandom = new SecureRandom();
	private final long expiresInSeconds;

	public RefreshTokenService(
			RefreshTokenMapper refreshTokenMapper,
			@Value("${centralauth.refresh-token.expires-in-seconds:2592000}") long expiresInSeconds) {
		this.refreshTokenMapper = refreshTokenMapper;
		this.expiresInSeconds = expiresInSeconds;
	}

	public String issueRefreshToken(String userId) {
		String token = createOpaqueToken();
		Instant now = Instant.now();
		refreshTokenMapper.insert(new RefreshToken(
				UUID.randomUUID().toString(),
				userId,
				hashToken(token),
				now,
				now.plusSeconds(expiresInSeconds),
				false,
				null,
				null,
				null));
		return token;
	}

	public void revokeRefreshToken(String userId, String refreshToken) {
		refreshTokenMapper.revokeByTokenHashAndUserId(hashToken(refreshToken), userId, Instant.now());
	}

	public void revokeAllActiveRefreshTokens(String userId) {
		refreshTokenMapper.revokeAllActiveForUser(userId, Instant.now());
	}

	private String createOpaqueToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return BASE64_URL_ENCODER.encodeToString(bytes);
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
