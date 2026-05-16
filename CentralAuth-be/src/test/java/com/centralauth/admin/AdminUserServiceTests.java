package com.centralauth.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.centralauth.event.auth.AdminUserStatusChangedEvent;
import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

class AdminUserServiceTests {

	@Test
	void updateAccountStatusPublishesAuditEventWithActorAndStatusTransition() {
		FakeUserMapper userMapper = new FakeUserMapper();
		CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
		userMapper.addUser(new User(
				"target-user-id",
				"target@example.com",
				"password-hash",
				"Target User",
				true,
				true,
				AccountStatus.ACTIVE,
				Instant.parse("2026-05-16T01:00:00Z"),
				Instant.parse("2026-05-16T01:00:00Z")));
		AdminUserService service = new AdminUserService(userMapper, eventPublisher);

		service.updateAccountStatus("target-user-id", AccountStatus.LOCKED, "admin-user-id");

		assertThat(eventPublisher.events).hasSize(1);
		AdminUserStatusChangedEvent event = (AdminUserStatusChangedEvent) eventPublisher.events.getFirst();
		assertThat(event.userId()).isEqualTo("target-user-id");
		assertThat(event.email()).isEqualTo("target@example.com");
		assertThat(event.previousStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(event.newStatus()).isEqualTo(AccountStatus.LOCKED);
		assertThat(event.adminUserId()).isEqualTo("admin-user-id");
		assertThat(event.occurredAt()).isNotNull();
	}

	private static final class CapturingEventPublisher implements ApplicationEventPublisher {

		private final List<Object> events = new ArrayList<>();

		@Override
		public void publishEvent(Object event) {
			events.add(event);
		}
	}

	private static final class FakeUserMapper implements UserMapper {

		private final Map<String, User> usersById = new HashMap<>();
		private AccountStatus updatedStatus;

		void addUser(User user) {
			usersById.put(user.id(), user);
		}

		@Override
		public Optional<User> findByEmail(String email) {
			return Optional.empty();
		}

		@Override
		public Optional<User> findById(String id) {
			return Optional.ofNullable(usersById.get(id))
					.map(user -> updatedStatus == null ? user : new User(
							user.id(),
							user.email(),
							user.passwordHash(),
							user.displayName(),
							updatedStatus == AccountStatus.ACTIVE,
							user.emailVerified(),
							updatedStatus,
							user.createdAt(),
							Instant.now()));
		}

		@Override
		public List<User> findAdminUsers(String email, AccountStatus accountStatus, int limit) {
			return List.of();
		}

		@Override
		public void insert(User user) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void insertRole(String userId, String role) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<String> findRolesByUserId(String userId) {
			return List.of("ROLE_USER");
		}

		@Override
		public int updateAccountStatus(
				String id,
				AccountStatus accountStatus,
				boolean enabled,
				boolean emailVerified) {
			updatedStatus = accountStatus;
			return usersById.containsKey(id) ? 1 : 0;
		}

		@Override
		public int verifyEmail(String email) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int updatePasswordHash(String id, String passwordHash) {
			throw new UnsupportedOperationException();
		}
	}
}
