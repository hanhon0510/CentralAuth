package com.centralauth.auth.dto;

public record CentralLoginContextResponse(
		String clientId,
		String clientName,
		String redirectUri,
		String state,
		String loginState) {
}
