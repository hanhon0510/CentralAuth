package com.centralauth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.centralauth.event.auth.AdminUserStatusChangedEvent;
import com.centralauth.event.auth.LoginFailedEvent;
import com.centralauth.user.AccountStatus;
import com.fasterxml.jackson.databind.json.JsonMapper;

class AuditLogServiceTests {

	private final CapturingAuditLogMapper mapper = new CapturingAuditLogMapper();
	private final AuditLogService service = new AuditLogService(mapper, JsonMapper.builder().findAndAddModules().build());

	@Test
	void recordStoresNormalizedLoginFailedEventWithKafkaMetadataAndPayload() {
		Instant occurredAt = Instant.parse("2026-05-16T01:02:03Z");
		LoginFailedEvent event = new LoginFailedEvent(
				"security@example.com",
				"203.0.113.42",
				"INVALID_CREDENTIALS",
				occurredAt);

		service.record(event, "auth.user.login.failed", "security@example.com");

		assertThat(mapper.inserted).hasSize(1);
		AuditLog auditLog = mapper.inserted.getFirst();
		assertThat(auditLog.id()).isNotBlank();
		assertThat(auditLog.eventType()).isEqualTo("LOGIN_FAILED");
		assertThat(auditLog.userId()).isNull();
		assertThat(auditLog.email()).isEqualTo("security@example.com");
		assertThat(auditLog.clientIp()).isEqualTo("203.0.113.42");
		assertThat(auditLog.reason()).isEqualTo("INVALID_CREDENTIALS");
		assertThat(auditLog.occurredAt()).isEqualTo(occurredAt);
		assertThat(auditLog.consumedAt()).isNotNull();
		assertThat(auditLog.kafkaTopic()).isEqualTo("auth.user.login.failed");
		assertThat(auditLog.kafkaKey()).isEqualTo("security@example.com");
		assertThat(auditLog.payloadJson()).contains("\"reason\":\"INVALID_CREDENTIALS\"");
	}

	@Test
	void recordStoresNormalizedAdminUserStatusChangedEvent() {
		Instant occurredAt = Instant.parse("2026-05-16T02:03:04Z");
		AdminUserStatusChangedEvent event = new AdminUserStatusChangedEvent(
				"target-user-id",
				"target@example.com",
				AccountStatus.ACTIVE,
				AccountStatus.LOCKED,
				"admin-user-id",
				occurredAt);

		service.record(event, "auth.admin.user.status.changed", "target-user-id");

		assertThat(mapper.inserted).hasSize(1);
		AuditLog auditLog = mapper.inserted.getFirst();
		assertThat(auditLog.eventType()).isEqualTo("ADMIN_USER_STATUS_CHANGED");
		assertThat(auditLog.userId()).isEqualTo("target-user-id");
		assertThat(auditLog.email()).isEqualTo("target@example.com");
		assertThat(auditLog.reason()).isEqualTo("ACTIVE_TO_LOCKED");
		assertThat(auditLog.occurredAt()).isEqualTo(occurredAt);
		assertThat(auditLog.kafkaTopic()).isEqualTo("auth.admin.user.status.changed");
		assertThat(auditLog.kafkaKey()).isEqualTo("target-user-id");
		assertThat(auditLog.payloadJson()).contains("\"adminUserId\":\"admin-user-id\"");
	}

	@Test
	void findRecentClampsLimitToSafeBounds() {
		service.findRecent(null, null, null, 500);

		assertThat(mapper.lastLimit).isEqualTo(200);

		service.findRecent(null, null, null, 0);

		assertThat(mapper.lastLimit).isEqualTo(1);
	}

	private static final class CapturingAuditLogMapper implements AuditLogMapper {

		private final List<AuditLog> inserted = new ArrayList<>();
		private int lastLimit;

		@Override
		public void insert(AuditLog auditLog) {
			inserted.add(auditLog);
		}

		@Override
		public List<AuditLog> findRecent(String eventType, String userId, String email, int limit) {
			lastLimit = limit;
			return List.of();
		}
	}
}
