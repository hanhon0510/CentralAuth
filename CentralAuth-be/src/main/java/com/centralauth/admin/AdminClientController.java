package com.centralauth.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.centralauth.admin.dto.AdminClientResponse;
import com.centralauth.admin.dto.CreateClientRequest;
import com.centralauth.admin.dto.UpdateClientActiveRequest;
import com.centralauth.admin.dto.UpdateClientRequest;
import com.centralauth.client.ClientApplicationService;
import com.centralauth.common.ApiResponse;
import com.centralauth.common.Messages;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/clients")
public class AdminClientController {

	private final ClientApplicationService clientApplicationService;
	private final Messages messages;

	public AdminClientController(ClientApplicationService clientApplicationService, Messages messages) {
		this.clientApplicationService = clientApplicationService;
		this.messages = messages;
	}

	@PostMapping
	public ApiResponse<AdminClientResponse> createClient(@Valid @RequestBody CreateClientRequest request) {
		return ApiResponse.success(
				messages.get("admin.clients.created"),
				AdminClientResponse.from(clientApplicationService.createClient(
						request.clientId(),
						request.clientName(),
						request.redirectUris(),
						request.allowedOrigins(),
						request.active() == null || request.active())));
	}

	@PutMapping("/{clientId}")
	public ApiResponse<AdminClientResponse> updateClient(
			@PathVariable String clientId,
			@Valid @RequestBody UpdateClientRequest request) {
		return ApiResponse.success(
				messages.get("admin.clients.updated"),
				AdminClientResponse.from(clientApplicationService.updateClient(
						clientId,
						request.clientName(),
						request.redirectUris(),
						request.allowedOrigins(),
						request.active())));
	}

	@GetMapping
	public ApiResponse<List<AdminClientResponse>> listClients() {
		return ApiResponse.success(
				messages.get("admin.clients.list"),
				clientApplicationService.listClients().stream()
						.map(AdminClientResponse::from)
						.toList());
	}

	@PatchMapping("/{clientId}/active")
	public ApiResponse<AdminClientResponse> updateActive(
			@PathVariable String clientId,
			@Valid @RequestBody UpdateClientActiveRequest request) {
		return ApiResponse.success(
				messages.get("admin.clients.active.updated"),
				AdminClientResponse.from(clientApplicationService.updateActive(clientId, request.active())));
	}
}
