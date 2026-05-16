package com.centralauth.event.auth;

import java.time.Instant;

public record LoginFailedEvent(
		String email,
		String clientIp,
		String reason,
		Instant occurredAt
) {
}
