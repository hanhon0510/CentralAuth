package com.centralauth.event.kafka;

import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.centralauth.config.KafkaTopicProperties;
import com.centralauth.event.auth.AdminUserStatusChangedEvent;
import com.centralauth.event.auth.LoginFailedEvent;
import com.centralauth.event.auth.LoginSucceededEvent;
import com.centralauth.event.auth.PasswordChangedEvent;
import com.centralauth.event.auth.PasswordResetRequestedEvent;
import com.centralauth.event.auth.UserLoggedOutEvent;
import com.centralauth.event.auth.UserRegisteredEvent;
import com.centralauth.event.auth.UserVerifiedEvent;

@Component
@ConditionalOnProperty(prefix = "centralauth.kafka", name = "enabled", havingValue = "true")
public class AuthEventKafkaPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final KafkaTopicProperties topics;

	public AuthEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicProperties topics) {
		this.kafkaTemplate = kafkaTemplate;
		this.topics = topics;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(UserRegisteredEvent event) {
		kafkaTemplate.send(topics.userRegistered(), event.userId(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(UserVerifiedEvent event) {
		kafkaTemplate.send(topics.userVerified(), event.userId(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(LoginSucceededEvent event) {
		kafkaTemplate.send(topics.loginSucceeded(), event.userId(), event);
	}

	@EventListener
	public void publish(LoginFailedEvent event) {
		kafkaTemplate.send(topics.loginFailed(), event.email(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(UserLoggedOutEvent event) {
		kafkaTemplate.send(topics.logout(), event.userId(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(PasswordResetRequestedEvent event) {
		kafkaTemplate.send(topics.passwordResetRequested(), event.userId(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(PasswordChangedEvent event) {
		kafkaTemplate.send(topics.passwordChanged(), event.userId(), event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(AdminUserStatusChangedEvent event) {
		kafkaTemplate.send(topics.adminUserStatusChanged(), event.userId(), event);
	}
}
