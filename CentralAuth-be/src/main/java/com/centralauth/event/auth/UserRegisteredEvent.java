package com.centralauth.event.auth;

import java.time.Instant;

public record UserRegisteredEvent(
		String userId,
		String email,
		String displayName,
		Instant occurredAt
) {
}
