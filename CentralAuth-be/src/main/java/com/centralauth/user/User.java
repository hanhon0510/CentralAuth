package com.centralauth.user;

import java.time.Instant;

public record User(
		String id,
		String email,
		String passwordHash,
		String displayName,
		boolean enabled,
		boolean emailVerified,
		Instant createdAt,
		Instant updatedAt) {
}
