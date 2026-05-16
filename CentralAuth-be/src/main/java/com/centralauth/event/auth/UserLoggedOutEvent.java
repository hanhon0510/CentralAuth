package com.centralauth.event.auth;

import java.time.Instant;

public record UserLoggedOutEvent(
		String userId,
		boolean allDevices,
		Instant occurredAt
) {
}
