package com.centralauth.security;

import java.util.List;

public record JwtPrincipal(String userId, String email, List<String> roles, String tokenUse, String audience) {

	public static final String CENTRAL_AUTH_ACCESS = "centralauth_access";
	public static final String CLIENT_ACCESS = "client_access";

	public boolean centralAuthAccessToken() {
		return CENTRAL_AUTH_ACCESS.equals(tokenUse);
	}
}
