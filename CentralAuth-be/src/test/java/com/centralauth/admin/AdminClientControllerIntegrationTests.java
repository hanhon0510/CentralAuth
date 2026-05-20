package com.centralauth.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AdminClientControllerIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:admin-client-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
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
	void clearData() {
		jdbcTemplate.execute("delete from clients");
		jdbcTemplate.execute("delete from user_roles");
		jdbcTemplate.execute("delete from users");
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	@Test
	void adminCanCreateListUpdateDisableAndEnableClientApplications() throws Exception {
		mockMvc().perform(post("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId": "dashboard-app",
								  "clientName": "Dashboard App",
								  "redirectUris": [
								    "https://dashboard.example.com/auth/callback",
								    "http://localhost:5173/auth/callback"
								  ],
								  "allowedOrigins": [
								    "https://dashboard.example.com",
								    "http://localhost:5173"
								  ],
								  "active": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Client created"))
				.andExpect(jsonPath("$.data.clientId").value("dashboard-app"))
				.andExpect(jsonPath("$.data.clientName").value("Dashboard App"))
				.andExpect(jsonPath("$.data.active").value(true))
				.andExpect(jsonPath("$.data.createdAt").exists())
				.andExpect(jsonPath("$.data.redirectUris[*]", containsInAnyOrder(
						"https://dashboard.example.com/auth/callback",
						"http://localhost:5173/auth/callback")))
				.andExpect(jsonPath("$.data.allowedOrigins[*]", containsInAnyOrder(
						"https://dashboard.example.com",
						"http://localhost:5173")));

		mockMvc().perform(get("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Clients"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].clientId").value("dashboard-app"));

		mockMvc().perform(put("/api/v1/admin/clients/dashboard-app")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientName": "Dashboard Console",
								  "redirectUris": ["https://console.example.com/callback"],
								  "allowedOrigins": ["https://console.example.com"],
								  "active": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Client updated"))
				.andExpect(jsonPath("$.data.clientId").value("dashboard-app"))
				.andExpect(jsonPath("$.data.clientName").value("Dashboard Console"))
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.redirectUris[0]").value("https://console.example.com/callback"))
				.andExpect(jsonPath("$.data.allowedOrigins[0]").value("https://console.example.com"));

		mockMvc().perform(patch("/api/v1/admin/clients/dashboard-app/active")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Client active status updated"))
				.andExpect(jsonPath("$.data.active").value(true));

		mockMvc().perform(patch("/api/v1/admin/clients/dashboard-app/active")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.active").value(false));
	}

	@Test
	void adminClientEndpointsRejectInvalidAndDuplicateClientMetadata() throws Exception {
		mockMvc().perform(post("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId": "bad-client",
								  "clientName": "Bad Client",
								  "redirectUris": ["https://app.example.com/callback#fragment"],
								  "allowedOrigins": ["https://app.example.com"],
								  "active": true
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid client metadata"));

		mockMvc().perform(post("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId": "dashboard-app",
								  "clientName": "Dashboard App",
								  "redirectUris": ["https://dashboard.example.com/auth/callback"],
								  "allowedOrigins": ["https://dashboard.example.com"],
								  "active": true
								}
								"""))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId": "dashboard-app",
								  "clientName": "Duplicate Dashboard App",
								  "redirectUris": ["https://dashboard.example.com/other"],
								  "allowedOrigins": ["https://dashboard.example.com"],
								  "active": true
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Client already exists"));
	}

	@Test
	void adminClientEndpointsRejectNonAdminUsers() throws Exception {
		mockMvc().perform(get("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + userToken()))
				.andExpect(status().isForbidden());

		mockMvc().perform(post("/api/v1/admin/clients")
						.header("Authorization", "Bearer " + userToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId": "dashboard-app",
								  "clientName": "Dashboard App",
								  "redirectUris": ["https://dashboard.example.com/auth/callback"],
								  "allowedOrigins": ["https://dashboard.example.com"],
								  "active": true
								}
								"""))
				.andExpect(status().isForbidden());
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
}
