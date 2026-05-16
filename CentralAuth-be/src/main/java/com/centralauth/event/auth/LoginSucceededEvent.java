package com.centralauth.event.auth;

import java.time.Instant;

public record LoginSucceededEvent(
		String userId,
		String email,
		String clientIp,
		Instant occurredAt
) {
}
