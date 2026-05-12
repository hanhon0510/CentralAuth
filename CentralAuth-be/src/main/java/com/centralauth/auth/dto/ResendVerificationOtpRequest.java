package com.centralauth.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationOtpRequest(@NotBlank @Email String email) {
}
