package com.centralauth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import com.centralauth.audit.AuditLogConsumer;
import com.centralauth.audit.AuditLogService;
import com.centralauth.event.kafka.AuthEventKafkaPublisher;

class KafkaIntegrationToggleTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
			.withBean(AuditLogService.class, () -> mock(AuditLogService.class))
			.withBean(KafkaTopicProperties.class, () -> new KafkaTopicProperties(
					"auth.user.registered",
					"auth.user.verified",
					"auth.user.login.succeeded",
					"auth.user.login.failed",
					"auth.user.logout",
					"auth.user.password.reset.requested",
					"auth.user.password.changed",
					"auth.admin.user.status.changed"))
			.withUserConfiguration(AuthEventKafkaPublisher.class, AuditLogConsumer.class);

	@Test
	void disablesKafkaPublisherAndConsumerWhenKafkaIsDisabled() {
		contextRunner
				.withPropertyValues("centralauth.kafka.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(AuthEventKafkaPublisher.class);
					assertThat(context).doesNotHaveBean(AuditLogConsumer.class);
				});
	}

	@Test
	void enablesKafkaPublisherAndConsumerWhenKafkaIsEnabled() {
		contextRunner
				.withPropertyValues("centralauth.kafka.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(AuthEventKafkaPublisher.class);
					assertThat(context).hasSingleBean(AuditLogConsumer.class);
				});
	}
}
