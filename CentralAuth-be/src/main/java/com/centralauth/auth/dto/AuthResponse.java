package com.centralauth.auth.dto;

public record AuthResponse(String token, String refreshToken, UserResponse user) {
}
