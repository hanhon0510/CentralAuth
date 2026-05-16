package com.centralauth.audit;

import java.time.Instant;

public record AuditLog(
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
}
