package com.centralauth.security;

import java.util.List;

public record JwtPrincipal(String userId, String email, List<String> roles) {
}
