package com.centralauth.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateClientActiveRequest(@NotNull Boolean active) {
}
