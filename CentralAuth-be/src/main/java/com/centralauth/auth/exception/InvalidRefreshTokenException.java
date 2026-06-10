package com.centralauth.auth.exception;

import com.centralauth.common.LocalizedApiException;

public class InvalidRefreshTokenException extends LocalizedApiException {

	public InvalidRefreshTokenException() {
		super("auth.error.invalidRefreshToken");
	}
}
