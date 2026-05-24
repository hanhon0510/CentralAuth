package com.centralauth.auth.exception;

import com.centralauth.common.LocalizedApiException;

public class InvalidCentralLoginStateException extends LocalizedApiException {

	public InvalidCentralLoginStateException() {
		super("auth.error.invalidCentralLoginState");
	}
}
