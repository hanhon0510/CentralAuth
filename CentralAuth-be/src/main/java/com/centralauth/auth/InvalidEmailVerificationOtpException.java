package com.centralauth.auth;

public class InvalidEmailVerificationOtpException extends RuntimeException {

	public InvalidEmailVerificationOtpException() {
		super("Invalid or expired email verification OTP");
	}
}
