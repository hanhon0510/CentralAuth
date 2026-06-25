package com.centralauth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "centralauth.email", name = "mode", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogEmailSender.class);

	@Override
	public void send(EmailMessage message) {
		LOGGER.info(
				"email delivery mode=log to={} from={} subject=\"{}\"\n{}",
				message.to(),
				message.from(),
				message.subject(),
				message.body());
	}
}
