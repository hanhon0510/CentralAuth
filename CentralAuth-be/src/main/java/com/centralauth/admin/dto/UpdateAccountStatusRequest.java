package com.centralauth.admin.dto;

import com.centralauth.user.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull AccountStatus status) {
}
