package com.centralauth.auth.exception;

import com.centralauth.common.LocalizedApiException;

public class DuplicateEmailException extends LocalizedApiException {

	public DuplicateEmailException() {
		super("auth.error.duplicateEmail");
	}
}
