package com.centralauth.auth.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class StructuredAuthLoggerTests {

	@Test
	void writesStructuredFieldsForLoginFailureWithoutSecrets() {
		try (CapturedLogs logs = captureLogs()) {
			StructuredAuthLogger.loginFailed(
					"user@example.com",
					"203.0.113.12",
					"INVALID_CREDENTIALS");

			assertThat(logs.events()).hasSize(1);
			ILoggingEvent event = logs.events().getFirst();
			assertThat(event.getLevel()).isEqualTo(Level.WARN);
			assertThat(event.getFormattedMessage()).isEqualTo("auth action");
			assertThat(keyValues(event)).containsAllEntriesOf(Map.of(
					"event", "auth.login",
					"outcome", "failure",
					"email", "user@example.com",
					"clientIp", "203.0.113.12",
					"reason", "INVALID_CREDENTIALS"));
			assertThat(event.getFormattedMessage()).doesNotContain("password", "otp", "token");
		}
	}

	@Test
	void writesStructuredFieldsForKeyAuthSuccessActions() {
		try (CapturedLogs logs = captureLogs()) {
			StructuredAuthLogger.userRegistered("user-1", "registered@example.com");
			StructuredAuthLogger.emailVerified("user-1", "registered@example.com");
			StructuredAuthLogger.passwordResetRequested("user-1", "registered@example.com");
			StructuredAuthLogger.passwordChanged("user-1", "registered@example.com");
			StructuredAuthLogger.loginSucceeded("user-1", "registered@example.com", "198.51.100.20");
			StructuredAuthLogger.loggedOut("user-1", true);
			StructuredAuthLogger.centralLoginCodeIssued("user-1", "dashboard-client");
			StructuredAuthLogger.clientTokenIssued("user-1", "dashboard-client");

			assertThat(logs.events())
					.extracting(ILoggingEvent::getFormattedMessage)
					.containsOnly("auth action");
			assertThat(logs.events())
					.extracting(event -> keyValues(event).get("event"))
					.containsExactly(
							"auth.user.registered",
							"auth.email.verified",
							"auth.password_reset.requested",
							"auth.password.changed",
							"auth.login",
							"auth.logout",
							"auth.central_login.code_issued",
							"auth.central_login.client_token_issued");
			assertThat(logs.events())
					.extracting(ILoggingEvent::getLevel)
					.containsOnly(Level.INFO);
		}
	}

	@Test
	void writesStructuredFieldsForVerificationAndResendFailures() {
		try (CapturedLogs logs = captureLogs()) {
			StructuredAuthLogger.emailVerificationFailed("user@example.com", "INVALID_OTP");
			StructuredAuthLogger.emailVerificationOtpResent("user-1", "user@example.com");
			StructuredAuthLogger.signupFailed("user@example.com", "DUPLICATE_EMAIL");

			assertThat(logs.events())
					.extracting(event -> keyValues(event).get("event"))
					.containsExactly(
							"auth.email.verification",
							"auth.email.otp_resent",
							"auth.user.registration");
			assertThat(logs.events())
					.extracting(event -> keyValues(event).get("outcome"))
					.containsExactly("failure", "success", "failure");
			assertThat(logs.events())
					.extracting(ILoggingEvent::getLevel)
					.containsExactly(Level.WARN, Level.INFO, Level.WARN);
		}
	}

	private CapturedLogs captureLogs() {
		Logger logger = logger();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		boolean additive = logger.isAdditive();
		logger.setAdditive(false);
		logger.addAppender(appender);
		return new CapturedLogs(logger, appender, additive);
	}

	private Logger logger() {
		return (Logger) LoggerFactory.getLogger(StructuredAuthLogger.class);
	}

	private Map<String, Object> keyValues(ILoggingEvent event) {
		return event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	private record CapturedLogs(
			Logger logger,
			ListAppender<ILoggingEvent> appender,
			boolean additive) implements AutoCloseable {

		private java.util.List<ILoggingEvent> events() {
			return appender.list;
		}

		@Override
		public void close() {
			logger.detachAppender(appender);
			logger.setAdditive(additive);
		}
	}
}
