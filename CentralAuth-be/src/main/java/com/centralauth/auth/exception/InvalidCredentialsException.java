package com.centralauth.auth.exception;

import com.centralauth.common.LocalizedApiException;

public class InvalidCredentialsException extends LocalizedApiException {

	public InvalidCredentialsException() {
		super("auth.error.invalidCredentials");
	}
}
