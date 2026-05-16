package com.centralauth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;
import com.fasterxml.jackson.databind.json.JsonMapper;

class JwtServiceTests {

	private final JwtService jwtService = new JwtService(
			JsonMapper.builder().findAndAddModules().build(),
			"test-secret-with-at-least-32-characters",
			"central-auth-test",
			3600);

	@Test
	void createTokenStoresRolesAndValidateReturnsThemInPrincipal() {
		User user = new User(
				"3363bd74-8657-4742-8880-763d2e8ff833",
				"admin@example.com",
				"password-hash",
				"Admin",
				true,
				true,
				AccountStatus.ACTIVE,
				null,
				null);

		String token = jwtService.createToken(user, List.of("ROLE_USER", "ROLE_ADMIN"));

		JwtPrincipal principal = jwtService.validate(token).orElseThrow();
		assertThat(principal.userId()).isEqualTo(user.id());
		assertThat(principal.email()).isEqualTo(user.email());
		assertThat(principal.roles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
	}
}
