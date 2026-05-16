package com.centralauth.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.event.auth.AdminUserStatusChangedEvent;
import com.centralauth.event.auth.LoginFailedEvent;
import com.centralauth.event.auth.LoginSucceededEvent;
import com.centralauth.event.auth.PasswordChangedEvent;
import com.centralauth.event.auth.PasswordResetRequestedEvent;
import com.centralauth.event.auth.UserLoggedOutEvent;
import com.centralauth.event.auth.UserRegisteredEvent;
import com.centralauth.event.auth.UserVerifiedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuditLogService {

	private static final int MIN_LIMIT = 1;
	private static final int MAX_LIMIT = 200;

	private final AuditLogMapper auditLogMapper;
	private final ObjectMapper objectMapper;

	public AuditLogService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
		this.auditLogMapper = auditLogMapper;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void record(Object event, String kafkaTopic, String kafkaKey) {
		AuditEventDetails details = detailsFor(event);
		auditLogMapper.insert(new AuditLog(
				UUID.randomUUID().toString(),
				details.eventType(),
				details.userId(),
				details.email(),
				details.clientIp(),
				details.reason(),
				details.occurredAt(),
				Instant.now(),
				kafkaTopic,
				kafkaKey,
				payloadJson(event)));
	}

	@Transactional(readOnly = true)
	public List<AuditLog> findRecent(String eventType, String userId, String email, int limit) {
		return auditLogMapper.findRecent(blankToNull(eventType), blankToNull(userId), blankToNull(email), clamp(limit));
	}

	private AuditEventDetails detailsFor(Object event) {
		if (event instanceof UserRegisteredEvent typed) {
			return new AuditEventDetails(
					"USER_REGISTERED",
					typed.userId(),
					typed.email(),
					null,
					null,
					typed.occurredAt());
		}
		if (event instanceof UserVerifiedEvent typed) {
			return new AuditEventDetails("USER_VERIFIED", typed.userId(), typed.email(), null, null, typed.occurredAt());
		}
		if (event instanceof LoginSucceededEvent typed) {
			return new AuditEventDetails(
					"LOGIN_SUCCEEDED",
					typed.userId(),
					typed.email(),
					typed.clientIp(),
					null,
					typed.occurredAt());
		}
		if (event instanceof LoginFailedEvent typed) {
			return new AuditEventDetails(
					"LOGIN_FAILED",
					null,
					typed.email(),
					typed.clientIp(),
					typed.reason(),
					typed.occurredAt());
		}
		if (event instanceof UserLoggedOutEvent typed) {
			return new AuditEventDetails("USER_LOGGED_OUT", typed.userId(), null, null, null, typed.occurredAt());
		}
		if (event instanceof PasswordResetRequestedEvent typed) {
			return new AuditEventDetails(
					"PASSWORD_RESET_REQUESTED",
					typed.userId(),
					typed.email(),
					null,
					null,
					typed.occurredAt());
		}
		if (event instanceof PasswordChangedEvent typed) {
			return new AuditEventDetails(
					"PASSWORD_CHANGED",
					typed.userId(),
					typed.email(),
					null,
					null,
					typed.occurredAt());
		}
		if (event instanceof AdminUserStatusChangedEvent typed) {
			return new AuditEventDetails(
					"ADMIN_USER_STATUS_CHANGED",
					typed.userId(),
					typed.email(),
					null,
					typed.previousStatus() + "_TO_" + typed.newStatus(),
					typed.occurredAt());
		}
		throw new IllegalArgumentException("Unsupported audit event type: " + event.getClass().getName());
	}

	private String payloadJson(Object event) {
		try {
			return objectMapper.writeValueAsString(event);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize audit event payload", ex);
		}
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private int clamp(int limit) {
		return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
	}

	private record AuditEventDetails(
			String eventType,
			String userId,
			String email,
			String clientIp,
			String reason,
			Instant occurredAt
	) {
	}
}
