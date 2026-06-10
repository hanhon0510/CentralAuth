package com.centralauth.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.centralauth.auth.exception.InvalidRefreshTokenException;

class GlobalExceptionHandlerTests {

	@Test
	void invalidRefreshTokenReturnsUnauthorizedLocalizedError() {
		StaticMessageSource messageSource = new StaticMessageSource();
		messageSource.addMessage(
				"auth.error.invalidRefreshToken",
				Locale.US,
				"Invalid or expired refresh token");
		GlobalExceptionHandler handler = new GlobalExceptionHandler(new Messages(messageSource));

		ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidRefreshToken(
				new InvalidRefreshTokenException());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().message()).isEqualTo("Invalid or expired refresh token");
	}
}
