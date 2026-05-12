package com.centralauth.auth;

public class EmailVerificationNotPendingException extends RuntimeException {

	public EmailVerificationNotPendingException() {
		super("Email verification is not pending for this email");
	}
}
