package com.centralauth.auth.dto;

public record AuthResponse(String token, UserResponse user) {
}
