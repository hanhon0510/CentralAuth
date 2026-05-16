package com.centralauth.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.centralauth.audit.dto.AuditLogResponse;
import com.centralauth.common.ApiResponse;
import com.centralauth.common.Messages;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

	private final AuditLogService auditLogService;
	private final Messages messages;

	public AuditLogController(AuditLogService auditLogService, Messages messages) {
		this.auditLogService = auditLogService;
		this.messages = messages;
	}

	@GetMapping
	public ApiResponse<List<AuditLogResponse>> findRecent(
			@RequestParam(required = false) String eventType,
			@RequestParam(required = false) String userId,
			@RequestParam(required = false) String email,
			@RequestParam(defaultValue = "50") int limit) {
		List<AuditLogResponse> logs = auditLogService.findRecent(eventType, userId, email, limit).stream()
				.map(AuditLogResponse::from)
				.toList();
		return ApiResponse.success(messages.get("audit.logs.list"), logs);
	}
}
