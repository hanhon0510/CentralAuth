package com.centralauth.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

class AdminBootstrapServiceTests {

	@Test
	void assignsAdminRoleToConfiguredExistingUsers() {
		FakeUserMapper userMapper = new FakeUserMapper();
		userMapper.addUser("user-1", "admin@example.com");

		AdminBootstrapService service = new AdminBootstrapService(userMapper,
				" admin@example.com , ADMIN@example.com ");

		service.assignConfiguredAdmins();

		assertThat(userMapper.rolesFor("user-1")).containsExactly("ROLE_ADMIN");
		assertThat(userMapper.insertedRoles()).containsExactly("user-1:ROLE_ADMIN");
	}

	@Test
	void doesNotDuplicateAdminRoleWhenItAlreadyExists() {
		FakeUserMapper userMapper = new FakeUserMapper();
		userMapper.addUser("user-1", "admin@example.com");
		userMapper.addExistingRole("user-1", "ROLE_ADMIN");

		AdminBootstrapService service = new AdminBootstrapService(userMapper, "admin@example.com");

		service.assignConfiguredAdmins();

		assertThat(userMapper.rolesFor("user-1")).containsExactly("ROLE_ADMIN");
		assertThat(userMapper.insertedRoles()).isEmpty();
	}

	@Test
	void ignoresBlankAndUnknownConfiguredEmails() {
		FakeUserMapper userMapper = new FakeUserMapper();
		userMapper.addUser("user-1", "known@example.com");

		AdminBootstrapService service = new AdminBootstrapService(userMapper,
				" , missing@example.com, known@example.com, ");

		service.assignConfiguredAdmins();

		assertThat(userMapper.rolesFor("user-1")).containsExactly("ROLE_ADMIN");
		assertThat(userMapper.insertedRoles()).containsExactly("user-1:ROLE_ADMIN");
	}

	private static final class FakeUserMapper implements UserMapper {

		private final Map<String, User> usersByEmail = new HashMap<>();
		private final Map<String, List<String>> rolesByUserId = new HashMap<>();
		private final List<String> insertedRoles = new ArrayList<>();

		void addUser(String id, String email) {
			usersByEmail.put(email.toLowerCase(Locale.ROOT),
					new User(id, email, "password-hash", "Test User", true, true, AccountStatus.ACTIVE, null, null));
			rolesByUserId.putIfAbsent(id, new ArrayList<>());
		}

		void addExistingRole(String userId, String role) {
			rolesByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(role);
		}

		List<String> rolesFor(String userId) {
			return rolesByUserId.getOrDefault(userId, List.of());
		}

		List<String> insertedRoles() {
			return insertedRoles;
		}

		@Override
		public Optional<User> findByEmail(String email) {
			return Optional.ofNullable(usersByEmail.get(email.toLowerCase(Locale.ROOT)));
		}

		@Override
		public Optional<User> findById(String id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<User> findAdminUsers(String email, AccountStatus accountStatus, int limit) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void insert(User user) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void insertRole(String userId, String role) {
			insertedRoles.add(userId + ":" + role);
			rolesByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(role);
		}

		@Override
		public List<String> findRolesByUserId(String userId) {
			return List.copyOf(rolesByUserId.getOrDefault(userId, List.of()));
		}

		@Override
		public int updateAccountStatus(
				String id,
				AccountStatus accountStatus,
				boolean enabled,
				boolean emailVerified) {
			throw new UnsupportedOperationException();
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
