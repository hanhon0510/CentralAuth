package com.centralauth.auth.dto;

public record CentralLoginRedirectResponse(String redirectUri, String code, String state, String redirectUrl) {
}
