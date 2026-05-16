package com.centralauth.event.auth;

import java.time.Instant;

public record PasswordChangedEvent(
		String userId,
		String email,
		Instant occurredAt
) {
}
