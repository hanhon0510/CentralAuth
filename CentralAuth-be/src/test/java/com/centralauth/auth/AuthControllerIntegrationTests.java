package com.centralauth.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthControllerIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:auth-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.data.redis.host", () -> "localhost");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void createSchema() {
		jdbcTemplate.execute("""
				create table if not exists users (
				    id uuid primary key,
				    email varchar(320) not null,
				    password_hash varchar(255) not null,
				    display_name varchar(120),
				    enabled boolean not null default true,
				    email_verified boolean not null default false,
				    created_at timestamp with time zone not null default current_timestamp,
				    updated_at timestamp with time zone not null default current_timestamp,
				    constraint users_email_key unique (email)
				)
				""");
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	@Test
	void signupCreatesUserAndReturnsToken() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"new.user@example.com","password":"Password123!","displayName":"New User"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Signup successful"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.user.email").value("new.user@example.com"))
				.andExpect(jsonPath("$.data.user.displayName").value("New User"));
	}

	@Test
	void signupRejectsDuplicateEmail() throws Exception {
		String body = """
				{"email":"duplicate@example.com","password":"Password123!","displayName":"First"}
				""";

		mockMvc().perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Email is already registered"));
	}

	@Test
	void signinReturnsTokenForValidCredentials() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"signin@example.com","password":"Password123!","displayName":"Signin User"}
								"""))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"signin@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Signin successful"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.user.email").value("signin@example.com"));
	}

	@Test
	void signinRejectsInvalidPassword() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"bad-password@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"bad-password@example.com","password":"wrong-password"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void meReturnsCurrentUserForBearerToken() throws Exception {
		MvcResult signup = mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"me@example.com","password":"Password123!","displayName":"Me User"}
								"""))
				.andExpect(status().isOk())
				.andReturn();

		String response = signup.getResponse().getContentAsString();
		String token = response.substring(response.indexOf("\"token\":\"") + 9);
		token = token.substring(0, token.indexOf('"'));

		mockMvc().perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Current user"))
				.andExpect(jsonPath("$.data.email").value("me@example.com"))
				.andExpect(jsonPath("$.data.displayName").value("Me User"));
	}
}
