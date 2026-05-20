package com.centralauth.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
		@NotBlank @Size(max = 255) String clientName,
		@NotEmpty List<@NotBlank @Size(max = 2048) String> redirectUris,
		@NotNull List<@NotBlank @Size(max = 512) String> allowedOrigins,
		@NotNull Boolean active
) {
}
