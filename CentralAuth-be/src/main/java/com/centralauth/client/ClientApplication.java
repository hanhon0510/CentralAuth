package com.centralauth.client;

import java.time.Instant;
import java.util.List;

public record ClientApplication(
		String clientId,
		String clientName,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		List<String> redirectUris,
		List<String> allowedOrigins
) {

	public ClientApplication(
			String clientId,
			String clientName,
			Boolean active,
			Instant createdAt,
			Instant updatedAt,
			List<String> redirectUris,
			List<String> allowedOrigins) {
		this(clientId, clientName, Boolean.TRUE.equals(active), createdAt, updatedAt, redirectUris, allowedOrigins);
	}
}
