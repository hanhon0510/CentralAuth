package com.centralauth.auth.dto;

import com.centralauth.user.User;

public record UserResponse(String id, String email, String displayName, boolean emailVerified) {

	public static UserResponse from(User user) {
		return new UserResponse(user.id(), user.email(), user.displayName(), user.emailVerified());
	}
}
