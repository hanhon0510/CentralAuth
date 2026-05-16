package com.centralauth.event.auth;

import java.time.Instant;

public record UserVerifiedEvent(
		String userId,
		String email,
		Instant occurredAt
) {
}
