package com.centralauth.event.auth;

import java.time.Instant;

public record PasswordResetRequestedEvent(
		String userId,
		String email,
		Instant occurredAt
) {
}
