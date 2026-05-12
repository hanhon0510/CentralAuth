package com.centralauth.auth;

public class EmailVerificationOtpResendThrottledException extends RuntimeException {

	private final int retryAfterSeconds;

	public EmailVerificationOtpResendThrottledException(int retryAfterSeconds) {
		super("Please wait %d seconds before requesting another verification OTP".formatted(retryAfterSeconds));
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public int retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
