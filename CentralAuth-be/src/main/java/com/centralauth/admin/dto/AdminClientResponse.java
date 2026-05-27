package com.centralauth.admin.dto;

import java.time.Instant;
import java.util.List;

import com.centralauth.client.ClientApplication;

public record AdminClientResponse(
		String clientId,
		String clientName,
		List<String> redirectUris,
		List<String> allowedOrigins,
		List<String> logoutUris,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {

	public static AdminClientResponse from(ClientApplication clientApplication) {
		return new AdminClientResponse(
				clientApplication.clientId(),
				clientApplication.clientName(),
				clientApplication.redirectUris(),
				clientApplication.allowedOrigins(),
				clientApplication.logoutUris(),
				clientApplication.active(),
				clientApplication.createdAt(),
				clientApplication.updatedAt());
	}
}
