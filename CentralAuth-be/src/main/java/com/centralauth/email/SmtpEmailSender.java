package com.centralauth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "centralauth.email", name = "mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	public SmtpEmailSender(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public void send(EmailMessage message) {
		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setTo(message.to());
		mailMessage.setFrom(message.from());
		mailMessage.setSubject(message.subject());
		mailMessage.setText(message.body());
		mailSender.send(mailMessage);
	}
}
