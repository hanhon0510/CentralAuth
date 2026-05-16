package com.centralauth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AuditLogMapperIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:audit-mapper-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("centralauth.kafka.audit.enabled", () -> "false");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	AuditLogMapper auditLogMapper;

	@Test
	void insertAndFindRecentPersistsAuditLogMetadataAndPayload() {
		String userId = UUID.randomUUID().toString();
		AuditLog auditLog = new AuditLog(
				UUID.randomUUID().toString(),
				"LOGIN_FAILED",
				userId,
				"audit-mapper@example.com",
				"203.0.113.10",
				"INVALID_CREDENTIALS",
				Instant.parse("2026-05-16T03:04:05Z"),
				Instant.parse("2026-05-16T03:04:06Z"),
				"auth.user.login.failed",
				"audit-mapper@example.com",
				"{\"reason\":\"INVALID_CREDENTIALS\"}");

		auditLogMapper.insert(auditLog);

		List<AuditLog> savedLogs = auditLogMapper.findRecent("LOGIN_FAILED", userId, "audit-mapper@example.com", 10);

		assertThat(savedLogs).hasSize(1);
		AuditLog saved = savedLogs.getFirst();
		assertThat(saved.id()).isEqualTo(auditLog.id());
		assertThat(saved.eventType()).isEqualTo("LOGIN_FAILED");
		assertThat(saved.userId()).isEqualTo(userId);
		assertThat(saved.email()).isEqualTo("audit-mapper@example.com");
		assertThat(saved.clientIp()).isEqualTo("203.0.113.10");
		assertThat(saved.reason()).isEqualTo("INVALID_CREDENTIALS");
		assertThat(saved.occurredAt()).isEqualTo(auditLog.occurredAt());
		assertThat(saved.consumedAt()).isEqualTo(auditLog.consumedAt());
		assertThat(saved.kafkaTopic()).isEqualTo("auth.user.login.failed");
		assertThat(saved.kafkaKey()).isEqualTo("audit-mapper@example.com");
		assertThat(saved.payloadJson()).contains("INVALID_CREDENTIALS");
	}
}
