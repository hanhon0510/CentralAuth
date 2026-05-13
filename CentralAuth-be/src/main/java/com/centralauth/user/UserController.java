package com.centralauth.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.centralauth.auth.AuthService;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.common.ApiResponse;
import com.centralauth.common.Messages;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final AuthService authService;
	private final Messages messages;

	public UserController(AuthService authService, Messages messages) {
		this.authService = authService;
		this.messages = messages;
	}

	@GetMapping("/me")
	public ApiResponse<UserResponse> me(Authentication authentication) {
		return ApiResponse.success(messages.get("auth.currentUser"), authService.currentUser((String) authentication.getPrincipal()));
	}
}
