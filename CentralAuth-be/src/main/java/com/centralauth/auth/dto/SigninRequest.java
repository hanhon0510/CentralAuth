package com.centralauth.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SigninRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 1, max = 120) String password) {
}
