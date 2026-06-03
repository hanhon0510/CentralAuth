package com.centralauth.auth.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public final class StructuredAuthLogger {

	private static final Logger log = LoggerFactory.getLogger(StructuredAuthLogger.class);
	private static final String MESSAGE = "auth action";

	private StructuredAuthLogger() {
	}

	public static void userRegistered(String userId, String email) {
		addUserEmail(info("auth.user.registered", "success"), userId, email).log(MESSAGE);
	}

	public static void signupFailed(String email, String reason) {
		add(warn("auth.user.registration", "failure"), "email", email)
				.addKeyValue("reason", reason)
				.log(MESSAGE);
	}

	public static void emailVerified(String userId, String email) {
		addUserEmail(info("auth.email.verified", "success"), userId, email).log(MESSAGE);
	}

	public static void emailVerificationFailed(String email, String reason) {
		add(warn("auth.email.verification", "failure"), "email", email)
				.addKeyValue("reason", reason)
				.log(MESSAGE);
	}

	public static void emailVerificationOtpResent(String userId, String email) {
		addUserEmail(info("auth.email.otp_resent", "success"), userId, email).log(MESSAGE);
	}

	public static void passwordResetRequested(String userId, String email) {
		addUserEmail(info("auth.password_reset.requested", "success"), userId, email).log(MESSAGE);
	}

	public static void passwordChanged(String userId, String email) {
		addUserEmail(info("auth.password.changed", "success"), userId, email).log(MESSAGE);
	}

	public static void loginSucceeded(String userId, String email, String clientIp) {
		add(addUserEmail(info("auth.login", "success"), userId, email), "clientIp", clientIp).log(MESSAGE);
	}

	public static void loginFailed(String email, String clientIp, String reason) {
		add(add(warn("auth.login", "failure"), "email", email), "clientIp", clientIp)
				.addKeyValue("reason", reason)
				.log(MESSAGE);
	}

	public static void loggedOut(String userId, boolean allDevices) {
		add(info("auth.logout", "success"), "userId", userId)
				.addKeyValue("allDevices", allDevices)
				.log(MESSAGE);
	}

	public static void centralLoginCodeIssued(String userId, String clientId) {
		add(add(info("auth.central_login.code_issued", "success"), "userId", userId), "clientId", clientId)
				.log(MESSAGE);
	}

	public static void clientTokenIssued(String userId, String clientId) {
		add(add(info("auth.central_login.client_token_issued", "success"), "userId", userId), "clientId", clientId)
				.log(MESSAGE);
	}

	private static LoggingEventBuilder info(String event, String outcome) {
		return log.atInfo()
				.addKeyValue("event", event)
				.addKeyValue("outcome", outcome);
	}

	private static LoggingEventBuilder warn(String event, String outcome) {
		return log.atWarn()
				.addKeyValue("event", event)
				.addKeyValue("outcome", outcome);
	}

	private static LoggingEventBuilder addUserEmail(
			LoggingEventBuilder builder,
			String userId,
			String email) {
		return add(add(builder, "userId", userId), "email", email);
	}

	private static LoggingEventBuilder add(LoggingEventBuilder builder, String key, Object value) {
		if (value == null) {
			return builder;
		}
		if (value instanceof String text && text.isBlank()) {
			return builder;
		}
		return builder.addKeyValue(key, value);
	}
}
