package com.centralauth.auth.exception;

import com.centralauth.common.LocalizedApiException;

public class InvalidAuthorizationCodeException extends LocalizedApiException {

	public InvalidAuthorizationCodeException() {
		super("auth.error.invalidAuthorizationCode");
	}
}
