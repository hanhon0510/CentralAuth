package com.centralauth.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.centralauth.admin.dto.AdminUserResponse;
import com.centralauth.admin.dto.UpdateAccountStatusRequest;
import com.centralauth.common.ApiResponse;
import com.centralauth.common.Messages;
import com.centralauth.user.AccountStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

	private final AdminUserService adminUserService;
	private final Messages messages;

	public AdminUserController(AdminUserService adminUserService, Messages messages) {
		this.adminUserService = adminUserService;
		this.messages = messages;
	}

	@GetMapping
	public ApiResponse<List<AdminUserResponse>> findUsers(
			@RequestParam(required = false) String email,
			@RequestParam(required = false) AccountStatus status,
			@RequestParam(defaultValue = "50") int limit) {
		return ApiResponse.success(messages.get("admin.users.list"), adminUserService.findUsers(email, status, limit));
	}

	@GetMapping("/{id}")
	public ApiResponse<AdminUserResponse> findUser(@PathVariable String id) {
		return ApiResponse.success(messages.get("admin.users.get"), adminUserService.findUser(id));
	}

	@PatchMapping("/{id}/status")
	public ApiResponse<AdminUserResponse> updateStatus(
			@PathVariable String id,
			Authentication authentication,
			@Valid @RequestBody UpdateAccountStatusRequest request) {
		return ApiResponse.success(
				messages.get("admin.users.status.updated"),
				adminUserService.updateAccountStatus(id, request.status(), authentication.getName()));
	}
}
