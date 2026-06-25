package com.centralauth.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

@Service
public class AuthEmailService {

	private final EmailSender emailSender;
	private final EmailDeliveryProperties properties;

	public AuthEmailService(EmailSender emailSender, EmailDeliveryProperties properties) {
		this.emailSender = emailSender;
		this.properties = properties;
	}

	public void sendVerificationOtp(String email, String otp) {
		emailSender.send(new EmailMessage(
				email,
				properties.from(),
				properties.appName() + " email verification code",
				"""
						Your %s email verification code is %s.

						This code expires in 10 minutes.
						"""
						.formatted(properties.appName(), otp)));
	}

	public void sendPasswordResetLink(String email, String token) {
		String resetUrl = properties.frontendBaseUrl()
				+ "/reset-password?token="
				+ URLEncoder.encode(token, StandardCharsets.UTF_8);
		emailSender.send(new EmailMessage(
				email,
				properties.from(),
				properties.appName() + " password reset",
				"""
						Use this %s password reset link to choose a new password:

						%s

						This link expires in 15 minutes.
						"""
						.formatted(properties.appName(), resetUrl)));
	}
}
