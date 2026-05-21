package com.centralauth.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CentralLoginRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 1, max = 120) String password,
		@NotBlank @Size(max = 120) String clientId,
		@NotBlank @Size(max = 2048) String redirectUri,
		@Size(max = 2048) String state) {
}
