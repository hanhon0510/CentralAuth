package com.centralauth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.centralauth.security.JwtService;
import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;

@SpringBootTest
class AdminUserControllerIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:admin-user-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("centralauth.kafka.audit.enabled", () -> "false");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtService jwtService;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.execute("delete from user_roles");
		jdbcTemplate.execute("delete from users");
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	@Test
	void adminCanListUsersWithAccountStateAndRoles() throws Exception {
		insertUser(
				"11111111-1111-1111-1111-111111111111",
				"active@example.com",
				"Active User",
				true,
				true,
				"ACTIVE",
				List.of("ROLE_USER"));
		insertUser(
				"22222222-2222-2222-2222-222222222222",
				"locked@example.com",
				"Locked User",
				false,
				true,
				"LOCKED",
				List.of("ROLE_USER", "ROLE_ADMIN"));

		mockMvc().perform(get("/api/v1/admin/users")
						.header("Authorization", "Bearer " + adminToken())
						.param("status", "LOCKED")
						.param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Users"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value("22222222-2222-2222-2222-222222222222"))
				.andExpect(jsonPath("$.data[0].email").value("locked@example.com"))
				.andExpect(jsonPath("$.data[0].displayName").value("Locked User"))
				.andExpect(jsonPath("$.data[0].enabled").value(false))
				.andExpect(jsonPath("$.data[0].emailVerified").value(true))
				.andExpect(jsonPath("$.data[0].accountStatus").value("LOCKED"))
				.andExpect(jsonPath("$.data[0].roles[*]", containsInAnyOrder("ROLE_USER", "ROLE_ADMIN")));
	}

	@Test
	void adminCanInspectSingleUserAccountState() throws Exception {
		insertUser(
				"33333333-3333-3333-3333-333333333333",
				"unverified@example.com",
				null,
				false,
				false,
				"UNVERIFIED",
				List.of("ROLE_USER"));

		mockMvc().perform(get("/api/v1/admin/users/33333333-3333-3333-3333-333333333333")
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("User"))
				.andExpect(jsonPath("$.data.id").value("33333333-3333-3333-3333-333333333333"))
				.andExpect(jsonPath("$.data.email").value("unverified@example.com"))
				.andExpect(jsonPath("$.data.displayName").doesNotExist())
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.emailVerified").value(false))
				.andExpect(jsonPath("$.data.accountStatus").value("UNVERIFIED"))
				.andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));
	}

	@Test
	void adminCanUpdateAccountStatus() throws Exception {
		insertUser(
				"44444444-4444-4444-4444-444444444444",
				"status@example.com",
				"Status User",
				true,
				true,
				"ACTIVE",
				List.of("ROLE_USER"));

		mockMvc().perform(patch("/api/v1/admin/users/44444444-4444-4444-4444-444444444444/status")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"LOCKED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Account status updated"))
				.andExpect(jsonPath("$.data.accountStatus").value("LOCKED"))
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.emailVerified").value(true));

		assertThat(accountStatusFor("status@example.com")).isEqualTo("LOCKED");
		assertThat(enabledFor("status@example.com")).isFalse();

		mockMvc().perform(patch("/api/v1/admin/users/44444444-4444-4444-4444-444444444444/status")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"UNVERIFIED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("UNVERIFIED"))
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.emailVerified").value(false));

		mockMvc().perform(patch("/api/v1/admin/users/44444444-4444-4444-4444-444444444444/status")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"ACTIVE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.enabled").value(true))
				.andExpect(jsonPath("$.data.emailVerified").value(true));

		mockMvc().perform(patch("/api/v1/admin/users/44444444-4444-4444-4444-444444444444/status")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"DISABLED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("DISABLED"))
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.emailVerified").value(true));
	}

	@Test
	void adminUserEndpointsRejectNonAdminUsers() throws Exception {
		insertUser(
				"55555555-5555-5555-5555-555555555555",
				"user@example.com",
				"User",
				true,
				true,
				"ACTIVE",
				List.of("ROLE_USER"));

		mockMvc().perform(get("/api/v1/admin/users")
						.header("Authorization", "Bearer " + userToken()))
				.andExpect(status().isForbidden());

		mockMvc().perform(patch("/api/v1/admin/users/55555555-5555-5555-5555-555555555555/status")
						.header("Authorization", "Bearer " + userToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"DISABLED"}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminUserEndpointsRejectLockedAdminToken() throws Exception {
		insertUser(
				"66666666-6666-6666-6666-666666666666",
				"locked-admin@example.com",
				"Locked Admin",
				false,
				true,
				"LOCKED",
				List.of("ROLE_USER", "ROLE_ADMIN"));

		mockMvc().perform(get("/api/v1/admin/users")
						.header("Authorization", "Bearer " + tokenFor(
								"66666666-6666-6666-6666-666666666666",
								"locked-admin@example.com",
								List.of("ROLE_USER", "ROLE_ADMIN"))))
				.andExpect(status().isUnauthorized());
	}

	private void insertUser(
			String id,
			String email,
			String displayName,
			boolean enabled,
			boolean emailVerified,
			String accountStatus,
			List<String> roles) {
		jdbcTemplate.update("""
				insert into users (id, email, password_hash, display_name, enabled, email_verified, account_status)
				values (cast(? as uuid), ?, ?, ?, ?, ?, ?)
				""", id, email, "password-hash", displayName, enabled, emailVerified, accountStatus);
		for (String role : roles) {
			jdbcTemplate.update(
					"insert into user_roles (user_id, role) values (cast(? as uuid), ?)",
					id,
					role);
		}
	}

	private String adminToken() {
		String id = UUID.randomUUID().toString();
		String email = "admin-" + id + "@example.com";
		insertUser(id, email, "Admin", true, true, "ACTIVE", List.of("ROLE_USER", "ROLE_ADMIN"));
		return tokenFor(id, email, List.of("ROLE_USER", "ROLE_ADMIN"));
	}

	private String userToken() {
		String id = UUID.randomUUID().toString();
		String email = "user-" + id + "@example.com";
		insertUser(id, email, "User", true, true, "ACTIVE", List.of("ROLE_USER"));
		return tokenFor(id, email, List.of("ROLE_USER"));
	}

	private String tokenFor(String id, String email, List<String> roles) {
		return jwtService.createToken(new User(
				id,
				email,
				"password-hash",
				"User",
				true,
				true,
				AccountStatus.ACTIVE,
				Instant.now(),
				Instant.now()), roles);
	}

	private String accountStatusFor(String email) {
		return jdbcTemplate.queryForObject(
				"select account_status from users where email = ?",
				String.class,
				email);
	}

	private boolean enabledFor(String email) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"select enabled from users where email = ?",
				Boolean.class,
				email));
	}
}
