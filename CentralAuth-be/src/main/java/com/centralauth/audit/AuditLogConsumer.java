package com.centralauth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "centralauth.kafka", name = "enabled", havingValue = "true")
public class AuditLogConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogConsumer.class);

	private final AuditLogService auditLogService;

	public AuditLogConsumer(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@KafkaListener(
			id = "central-auth-audit-log-consumer",
			groupId = "${spring.kafka.consumer.group-id:central-auth-audit}",
			topics = {
					"${centralauth.kafka.topics.user-registered}",
					"${centralauth.kafka.topics.user-verified}",
					"${centralauth.kafka.topics.login-succeeded}",
					"${centralauth.kafka.topics.login-failed}",
					"${centralauth.kafka.topics.logout}",
					"${centralauth.kafka.topics.password-reset-requested}",
					"${centralauth.kafka.topics.password-changed}",
					"${centralauth.kafka.topics.admin-user-status-changed}"
			},
			autoStartup = "${centralauth.kafka.audit.enabled:true}")
	public void consume(
			Object event,
			@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
		try {
			auditLogService.record(event, topic, key);
		}
		catch (RuntimeException ex) {
			LOGGER.error(
					"Failed to persist audit event topic={} key={} eventType={}",
					topic,
					key,
					event.getClass().getName(),
					ex);
			throw ex;
		}
	}
}
