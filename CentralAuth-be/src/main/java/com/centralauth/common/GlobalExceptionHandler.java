package com.centralauth.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.centralauth.admin.AdminUserNotFoundException;
import com.centralauth.auth.exception.DuplicateEmailException;
import com.centralauth.auth.exception.EmailVerificationNotPendingException;
import com.centralauth.auth.exception.EmailVerificationOtpResendThrottledException;
import com.centralauth.auth.exception.InvalidAuthorizationCodeException;
import com.centralauth.auth.exception.InvalidCentralLoginStateException;
import com.centralauth.auth.exception.InvalidEmailVerificationOtpException;
import com.centralauth.auth.exception.InvalidCredentialsException;
import com.centralauth.auth.exception.InvalidPasswordResetTokenException;
import com.centralauth.auth.login.LoginRateLimitExceededException;
import com.centralauth.auth.login.LoginTemporarilyLockedException;
import com.centralauth.client.ClientApplicationNotFoundException;
import com.centralauth.client.DuplicateClientApplicationException;
import com.centralauth.client.InactiveClientApplicationException;
import com.centralauth.client.InvalidClientMetadataException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private final Messages messages;

	public GlobalExceptionHandler(Messages messages) {
		this.messages = messages;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		return error(messages.get("error.invalidRequest"), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
		return error(messages.get(ex), HttpStatus.CONFLICT);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
		return error(messages.get(ex), HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(InvalidEmailVerificationOtpException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEmailVerificationOtp(InvalidEmailVerificationOtpException ex) {
		return error(messages.get(ex), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidPasswordResetTokenException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
		return error(messages.get(ex), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidAuthorizationCodeException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidAuthorizationCode(InvalidAuthorizationCodeException ex) {
		return error(messages.get(ex), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidCentralLoginStateException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidCentralLoginState(InvalidCentralLoginStateException ex) {
		return error(messages.get(ex), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(EmailVerificationNotPendingException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmailVerificationNotPending(EmailVerificationNotPendingException ex) {
		return error(messages.get(ex), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(EmailVerificationOtpResendThrottledException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmailVerificationOtpResendThrottled(
			EmailVerificationOtpResendThrottledException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
				.body(ApiResponse.error(messages.get(ex)));
	}

	@ExceptionHandler(LoginTemporarilyLockedException.class)
	public ResponseEntity<ApiResponse<Void>> handleLoginTemporarilyLocked(LoginTemporarilyLockedException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
				.body(ApiResponse.error(messages.get(ex)));
	}

	@ExceptionHandler(LoginRateLimitExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleLoginRateLimitExceeded(LoginRateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
				.body(ApiResponse.error(messages.get(ex)));
	}

	@ExceptionHandler(AdminUserNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleAdminUserNotFound(AdminUserNotFoundException ex) {
		return error(messages.get("admin.users.error.notFound"), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(ClientApplicationNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleClientApplicationNotFound(ClientApplicationNotFoundException ex) {
		return error(messages.get("admin.clients.error.notFound"), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(DuplicateClientApplicationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateClientApplication(DuplicateClientApplicationException ex) {
		return error(messages.get("admin.clients.error.duplicate"), HttpStatus.CONFLICT);
	}

	@ExceptionHandler(InvalidClientMetadataException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidClientMetadata(InvalidClientMetadataException ex) {
		return error(messages.get("admin.clients.error.invalidMetadata"), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InactiveClientApplicationException.class)
	public ResponseEntity<ApiResponse<Void>> handleInactiveClientApplication(InactiveClientApplicationException ex) {
		return error(messages.get("admin.clients.error.inactive"), HttpStatus.BAD_REQUEST);
	}

	private ResponseEntity<ApiResponse<Void>> error(String message, HttpStatus status) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
