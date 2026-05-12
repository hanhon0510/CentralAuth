package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class EmailVerificationNotPendingException extends LocalizedApiException {

	public EmailVerificationNotPendingException() {
		super("auth.error.emailVerificationNotPending");
	}
}
