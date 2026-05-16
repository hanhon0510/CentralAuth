package com.centralauth.auth.login;

import com.centralauth.common.LocalizedApiException;

public class LoginTemporarilyLockedException extends LocalizedApiException {

	private final int retryAfterSeconds;

	public LoginTemporarilyLockedException(int retryAfterSeconds) {
		super("auth.error.loginTemporarilyLocked", retryAfterSeconds);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public int retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
