package com.centralauth.common;

public class LocalizedApiException extends RuntimeException {

	private final String messageCode;
	private final Object[] messageArgs;

	protected LocalizedApiException(String messageCode, Object... messageArgs) {
		super(messageCode);
		this.messageCode = messageCode;
		this.messageArgs = messageArgs.clone();
	}

	public String messageCode() {
		return messageCode;
	}

	public Object[] messageArgs() {
		return messageArgs.clone();
	}
}
