package com.centralauth.auth;

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
