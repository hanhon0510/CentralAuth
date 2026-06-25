package com.centralauth.email;

public record EmailMessage(
		String to,
		String from,
		String subject,
		String body) {
}
