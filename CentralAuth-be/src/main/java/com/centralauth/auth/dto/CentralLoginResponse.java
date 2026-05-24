package com.centralauth.auth.dto;

public record CentralLoginResponse(String redirectUri, String code, String state, String redirectUrl) {
}
