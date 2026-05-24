package com.centralauth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CentralLoginContinueRequest(
		@NotBlank @Size(max = 120) String clientId,
		@NotBlank @Size(max = 2048) String redirectUri,
		@Size(max = 2048) String state,
		@NotBlank @Size(max = 2048) String loginState) {
}
