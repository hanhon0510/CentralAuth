package com.centralauth.security;

public record JwtPrincipal(String userId, String email) {
}
