package com.centralauth.audit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.centralauth.event.auth.LoginSucceededEvent;

class AuditLogConsumerTests {

	@Test
	void consumeDelegatesEventWithTopicAndKeyToAuditService() {
		AuditLogService auditLogService = mock(AuditLogService.class);
		AuditLogConsumer consumer = new AuditLogConsumer(auditLogService);
		LoginSucceededEvent event = new LoginSucceededEvent(
				"4c4c0936-02a8-4fdc-b3bf-064d24cbb761",
				"login@example.com",
				"203.0.113.99",
				Instant.parse("2026-05-16T02:03:04Z"));

		consumer.consume(event, "auth.user.login.succeeded", "4c4c0936-02a8-4fdc-b3bf-064d24cbb761");

		verify(auditLogService).record(event, "auth.user.login.succeeded", "4c4c0936-02a8-4fdc-b3bf-064d24cbb761");
	}
}
