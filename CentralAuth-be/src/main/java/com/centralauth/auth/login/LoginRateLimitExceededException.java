package com.centralauth.auth.login;

import com.centralauth.common.LocalizedApiException;

public class LoginRateLimitExceededException extends LocalizedApiException {

	private final int retryAfterSeconds;

	public LoginRateLimitExceededException(int retryAfterSeconds) {
		super("auth.error.loginRateLimited", retryAfterSeconds);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public int retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
