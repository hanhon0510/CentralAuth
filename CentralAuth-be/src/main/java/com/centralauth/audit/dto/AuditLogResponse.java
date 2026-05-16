package com.centralauth.audit.dto;

import java.time.Instant;

import com.centralauth.audit.AuditLog;

public record AuditLogResponse(
		String id,
		String eventType,
		String userId,
		String email,
		String clientIp,
		String reason,
		Instant occurredAt,
		Instant consumedAt,
		String kafkaTopic,
		String kafkaKey,
		String payloadJson
) {

	public static AuditLogResponse from(AuditLog auditLog) {
		return new AuditLogResponse(
				auditLog.id(),
				auditLog.eventType(),
				auditLog.userId(),
				auditLog.email(),
				auditLog.clientIp(),
				auditLog.reason(),
				auditLog.occurredAt(),
				auditLog.consumedAt(),
				auditLog.kafkaTopic(),
				auditLog.kafkaKey(),
				auditLog.payloadJson());
	}
}
