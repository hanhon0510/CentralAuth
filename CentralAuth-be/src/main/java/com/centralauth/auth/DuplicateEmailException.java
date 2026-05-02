package com.centralauth.auth;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("Email is already registered");
	}
}
