package com.centralauth.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.centralauth.config.KafkaTopicProperties;

@Component
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
}
