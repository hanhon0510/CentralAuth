package com.centralauth.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Messages {

	private final MessageSource messageSource;

	public Messages(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String get(String code, Object... args) {
		return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
	}

	public String get(LocalizedApiException exception) {
		return get(exception.messageCode(), exception.messageArgs());
	}
}
