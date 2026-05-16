package com.centralauth.user;

import java.time.Instant;

public record User(
		String id,
		String email,
		String passwordHash,
		String displayName,
		boolean enabled,
		boolean emailVerified,
		AccountStatus accountStatus,
		Instant createdAt,
		Instant updatedAt) {
}
