package com.centralauth.event;

import java.time.Instant;

public record UserVerifiedEvent(
		String userId,
		String email,
		Instant occurredAt
) {
}
