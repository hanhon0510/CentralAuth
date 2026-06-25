package com.centralauth.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuthEmailServiceTests {

	private final CapturingEmailSender emailSender = new CapturingEmailSender();
	private final EmailDeliveryProperties properties = new EmailDeliveryProperties(
			"log",
			"CentralAuth",
			"noreply@centralauth.local",
			"http://localhost:5173");
	private final AuthEmailService service = new AuthEmailService(emailSender, properties);

	@Test
	void sendsVerificationOtpEmail() {
		service.sendVerificationOtp("user@example.com", "123456");

		assertThat(emailSender.messages()).singleElement()
				.satisfies(message -> {
					assertThat(message.to()).isEqualTo("user@example.com");
					assertThat(message.from()).isEqualTo("noreply@centralauth.local");
					assertThat(message.subject()).isEqualTo("CentralAuth email verification code");
					assertThat(message.body()).contains("123456", "10 minutes");
				});
	}

	@Test
	void sendsPasswordResetLinkEmail() {
		service.sendPasswordResetLink("user@example.com", "reset-token");

		assertThat(emailSender.messages()).singleElement()
				.satisfies(message -> {
					assertThat(message.to()).isEqualTo("user@example.com");
					assertThat(message.from()).isEqualTo("noreply@centralauth.local");
					assertThat(message.subject()).isEqualTo("CentralAuth password reset");
					assertThat(message.body())
							.contains("http://localhost:5173/reset-password?token=reset-token")
							.contains("15 minutes");
				});
	}

	private static final class CapturingEmailSender implements EmailSender {

		private final List<EmailMessage> messages = new ArrayList<>();

		@Override
		public void send(EmailMessage message) {
			messages.add(message);
		}

		private List<EmailMessage> messages() {
			return messages;
		}
	}
}
