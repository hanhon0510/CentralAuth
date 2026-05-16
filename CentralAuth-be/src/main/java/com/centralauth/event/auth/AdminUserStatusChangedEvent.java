package com.centralauth.event.auth;

import java.time.Instant;

import com.centralauth.user.AccountStatus;

public record AdminUserStatusChangedEvent(
		String userId,
		String email,
		AccountStatus previousStatus,
		AccountStatus newStatus,
		String adminUserId,
		Instant occurredAt
) {
}
