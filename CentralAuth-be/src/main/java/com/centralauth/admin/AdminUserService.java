package com.centralauth.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.admin.dto.AdminUserResponse;
import com.centralauth.event.auth.AdminUserStatusChangedEvent;
import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

@Service
public class AdminUserService {

	private static final int MIN_LIMIT = 1;
	private static final int MAX_LIMIT = 200;

	private final UserMapper userMapper;
	private final ApplicationEventPublisher eventPublisher;

	public AdminUserService(UserMapper userMapper, ApplicationEventPublisher eventPublisher) {
		this.userMapper = userMapper;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public List<AdminUserResponse> findUsers(String email, AccountStatus accountStatus, int limit) {
		return userMapper.findAdminUsers(blankToNull(email), accountStatus, clamp(limit)).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public AdminUserResponse findUser(String id) {
		return userMapper.findById(id)
				.map(this::toResponse)
				.orElseThrow(AdminUserNotFoundException::new);
	}

	@Transactional
	public AdminUserResponse updateAccountStatus(String id, AccountStatus accountStatus, String adminUserId) {
		User user = userMapper.findById(id).orElseThrow(AdminUserNotFoundException::new);
		AccountFlags flags = flagsFor(accountStatus, user.emailVerified());
		int updated = userMapper.updateAccountStatus(id, accountStatus, flags.enabled(), flags.emailVerified());
		if (updated == 0) {
			throw new AdminUserNotFoundException();
		}
		eventPublisher.publishEvent(new AdminUserStatusChangedEvent(
				user.id(),
				user.email(),
				user.accountStatus(),
				accountStatus,
				adminUserId,
				Instant.now()));
		return findUser(id);
	}

	private AdminUserResponse toResponse(User user) {
		return AdminUserResponse.from(user, userMapper.findRolesByUserId(user.id()));
	}

	private AccountFlags flagsFor(AccountStatus accountStatus, boolean currentEmailVerified) {
		return switch (accountStatus) {
			case ACTIVE -> new AccountFlags(true, true);
			case DISABLED, LOCKED -> new AccountFlags(false, currentEmailVerified);
			case UNVERIFIED -> new AccountFlags(false, false);
		};
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

	private record AccountFlags(boolean enabled, boolean emailVerified) {
	}
}
