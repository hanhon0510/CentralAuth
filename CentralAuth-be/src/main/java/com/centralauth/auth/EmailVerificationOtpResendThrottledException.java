package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class EmailVerificationOtpResendThrottledException extends LocalizedApiException {

	private final int retryAfterSeconds;

	public EmailVerificationOtpResendThrottledException(int retryAfterSeconds) {
		super("auth.error.verificationOtpResendThrottled", retryAfterSeconds);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public int retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
