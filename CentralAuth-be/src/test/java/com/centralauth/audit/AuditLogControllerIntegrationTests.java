package com.centralauth.audit;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuditLogControllerIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:audit-controller-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
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
	AuditLogMapper auditLogMapper;

	@Autowired
	JwtService jwtService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	@Test
	void auditLogViewerReturnsFilteredAuditLogsForAdminUser() throws Exception {
		String loginFailedId = UUID.randomUUID().toString();
		auditLogMapper.insert(new AuditLog(
				loginFailedId,
				"LOGIN_FAILED",
				null,
				"viewer@example.com",
				"203.0.113.20",
				"INVALID_CREDENTIALS",
				Instant.parse("2026-05-16T04:00:00Z"),
				Instant.parse("2026-05-16T04:00:01Z"),
				"auth.user.login.failed",
				"viewer@example.com",
				"{\"reason\":\"INVALID_CREDENTIALS\"}"));
		auditLogMapper.insert(new AuditLog(
				UUID.randomUUID().toString(),
				"LOGIN_SUCCEEDED",
				UUID.randomUUID().toString(),
				"viewer@example.com",
				"203.0.113.21",
				null,
				Instant.parse("2026-05-16T04:01:00Z"),
				Instant.parse("2026-05-16T04:01:01Z"),
				"auth.user.login.succeeded",
				UUID.randomUUID().toString(),
				"{\"email\":\"viewer@example.com\"}"));
		String token = jwtService.createToken(new User(
				insertUser("auditor@example.com", true),
				"auditor@example.com",
				"password-hash",
				"Auditor",
				true,
				true,
				AccountStatus.ACTIVE,
				null,
				null), List.of("ROLE_USER", "ROLE_ADMIN"));

		mockMvc().perform(get("/api/v1/audit-logs")
						.header("Authorization", "Bearer " + token)
						.param("eventType", "LOGIN_FAILED")
						.param("limit", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Audit logs"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(loginFailedId))
				.andExpect(jsonPath("$.data[0].eventType").value("LOGIN_FAILED"))
				.andExpect(jsonPath("$.data[0].email").value("viewer@example.com"))
				.andExpect(jsonPath("$.data[0].clientIp").value("203.0.113.20"))
				.andExpect(jsonPath("$.data[0].reason").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.data[0].payloadJson").value("{\"reason\":\"INVALID_CREDENTIALS\"}"));
	}

	@Test
	void auditLogViewerRejectsNonAdminUser() throws Exception {
		String token = jwtService.createToken(new User(
				insertUser("user@example.com", false),
				"user@example.com",
				"password-hash",
				"User",
				true,
				true,
				AccountStatus.ACTIVE,
				null,
				null), List.of("ROLE_USER"));

		mockMvc().perform(get("/api/v1/audit-logs")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	private String insertUser(String email, boolean admin) {
		String id = UUID.randomUUID().toString();
		jdbcTemplate.update("""
				insert into users (id, email, password_hash, display_name, enabled, email_verified, account_status)
				values (cast(? as uuid), ?, ?, ?, true, true, 'ACTIVE')
				""", id, email, "password-hash", admin ? "Auditor" : "User");
		jdbcTemplate.update("insert into user_roles (user_id, role) values (cast(? as uuid), ?)", id, "ROLE_USER");
		if (admin) {
			jdbcTemplate.update("insert into user_roles (user_id, role) values (cast(? as uuid), ?)", id, "ROLE_ADMIN");
		}
		return id;
	}
}
