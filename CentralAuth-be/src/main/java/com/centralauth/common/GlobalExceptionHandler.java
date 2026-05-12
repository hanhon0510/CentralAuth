package com.centralauth.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.centralauth.auth.DuplicateEmailException;
import com.centralauth.auth.EmailVerificationNotPendingException;
import com.centralauth.auth.EmailVerificationOtpResendThrottledException;
import com.centralauth.auth.InvalidEmailVerificationOtpException;
import com.centralauth.auth.InvalidCredentialsException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		return error("Invalid request", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
		return error(ex.getMessage(), HttpStatus.CONFLICT);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
		return error(ex.getMessage(), HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(InvalidEmailVerificationOtpException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEmailVerificationOtp(InvalidEmailVerificationOtpException ex) {
		return error(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(EmailVerificationNotPendingException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmailVerificationNotPending(EmailVerificationNotPendingException ex) {
		return error(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(EmailVerificationOtpResendThrottledException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmailVerificationOtpResendThrottled(
			EmailVerificationOtpResendThrottledException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
				.body(ApiResponse.error(ex.getMessage()));
	}

	private ResponseEntity<ApiResponse<Void>> error(String message, HttpStatus status) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
