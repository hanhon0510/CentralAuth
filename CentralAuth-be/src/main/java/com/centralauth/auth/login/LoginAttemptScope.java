package com.centralauth.auth.login;

import org.springframework.lang.NonNull;

enum LoginAttemptScope {
	EMAIL("email"),
	IP("ip");

	private static final String FAILURE_KEY_PREFIX = "login-failure:";
	private static final String RATE_KEY_PREFIX = "login-rate:";
	private static final String LOCK_KEY_PREFIX = "login-lock:";

	private final String keySegment;

	LoginAttemptScope(String keySegment) {
		this.keySegment = keySegment;
	}

	@NonNull
	String failureKey(@NonNull String value) {
		return key(FAILURE_KEY_PREFIX, value);
	}

	@NonNull
	String rateKey(@NonNull String value) {
		return key(RATE_KEY_PREFIX, value);
	}

	@NonNull
	String lockKey(@NonNull String value) {
		return key(LOCK_KEY_PREFIX, value);
	}

	@NonNull
	private String key(@NonNull String prefix, @NonNull String value) {
		return prefix + keySegment + ":" + value;
	}
}
