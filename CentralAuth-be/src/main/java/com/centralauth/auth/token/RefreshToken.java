package com.centralauth.auth.token;

import java.time.Instant;

public record RefreshToken(
		String id,
		String userId,
		String tokenHash,
		Instant issuedAt,
		Instant expiresAt,
		boolean revoked,
		Instant revokedAt,
		Instant createdAt,
		Instant updatedAt) {
}
