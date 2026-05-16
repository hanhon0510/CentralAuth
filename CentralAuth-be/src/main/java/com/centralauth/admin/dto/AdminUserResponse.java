package com.centralauth.admin.dto;

import java.time.Instant;
import java.util.List;

import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;

public record AdminUserResponse(
		String id,
		String email,
		String displayName,
		boolean enabled,
		boolean emailVerified,
		AccountStatus accountStatus,
		List<String> roles,
		Instant createdAt,
		Instant updatedAt
) {

	public static AdminUserResponse from(User user, List<String> roles) {
		return new AdminUserResponse(
				user.id(),
				user.email(),
				user.displayName(),
				user.enabled(),
				user.emailVerified(),
				user.accountStatus(),
				roles,
				user.createdAt(),
				user.updatedAt());
	}
}
