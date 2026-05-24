package com.centralauth.auth.dto;

public record CentralLoginTokenResponse(String token, UserResponse user) {
}
