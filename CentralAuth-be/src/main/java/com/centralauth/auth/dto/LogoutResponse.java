package com.centralauth.auth.dto;

import java.util.List;

public record LogoutResponse(List<String> logoutUris) {
}
