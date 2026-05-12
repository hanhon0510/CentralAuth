package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class InvalidEmailVerificationOtpException extends LocalizedApiException {

	public InvalidEmailVerificationOtpException() {
		super("auth.error.invalidEmailVerificationOtp");
	}
}
