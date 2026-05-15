package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class InvalidPasswordResetTokenException extends LocalizedApiException {

	public InvalidPasswordResetTokenException() {
		super("auth.error.invalidPasswordResetToken");
	}
}
