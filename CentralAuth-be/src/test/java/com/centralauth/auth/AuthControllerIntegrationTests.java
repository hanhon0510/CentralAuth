package com.centralauth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

	@MockitoBean
	StringRedisTemplate redisTemplate;

	ValueOperations<String, String> valueOperations;

	@BeforeEach
	void createSchema() {
		jdbcTemplate.execute("""
				create table if not exists users (
				    id uuid primary key,
				    email varchar(320) not null,
				    password_hash varchar(255) not null,
				    display_name varchar(120),
				    enabled boolean not null default false,
				    email_verified boolean not null default false,
				    created_at timestamp with time zone not null default current_timestamp,
				    updated_at timestamp with time zone not null default current_timestamp,
				    constraint users_email_key unique (email)
				)
				""");
		jdbcTemplate.execute("""
				create table if not exists user_roles (
				    user_id uuid not null,
				    role varchar(64) not null,
				    created_at timestamp with time zone not null default current_timestamp,
				    constraint user_roles_pk primary key (user_id, role),
				    constraint user_roles_user_id_fk foreign key (user_id) references users (id) on delete cascade
				)
				""");
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void configureRedis() {
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	private String captureSignupOtp(String email) {
		ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
				eq("email-verification:" + email),
				otpCaptor.capture(),
				eq(Duration.ofMinutes(10)));
		return otpCaptor.getValue();
	}

	private List<String> captureIssuedOtps(String email, int count) {
		ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations, times(count)).set(
				eq("email-verification:" + email),
				otpCaptor.capture(),
				eq(Duration.ofMinutes(10)));
		return otpCaptor.getAllValues();
	}

	private void verifyEmail(String email, String otp) throws Exception {
		when(valueOperations.get("email-verification:" + email)).thenReturn(otp);

		mockMvc().perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","otp":"%s"}
								""".formatted(email, otp)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Email verified"));
	}

	private String tokenFrom(MvcResult result) throws Exception {
		return stringFieldFrom(result, "token");
	}

	private String refreshTokenFrom(MvcResult result) throws Exception {
		return stringFieldFrom(result, "refreshToken");
	}

	private String stringFieldFrom(MvcResult result, String fieldName) throws Exception {
		String response = result.getResponse().getContentAsString();
		String marker = "\"" + fieldName + "\":\"";
		int start = response.indexOf(marker);
		assertThat(start).isNotNegative();
		String value = response.substring(start + marker.length());
		return value.substring(0, value.indexOf('"'));
	}

	private MvcResult signin(String email, String password) throws Exception {
		return mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
	}

	private void signupAndVerify(String email) throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","displayName":"Session User"}
								""".formatted(email)))
				.andExpect(status().isOk());
		verifyEmail(email, captureSignupOtp(email));
	}

	private int activeRefreshTokenCount(String email) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from refresh_tokens rt
				join users u on u.id = rt.user_id
				where u.email = ?
				  and rt.revoked = false
				  and rt.expires_at > current_timestamp
				""", Integer.class, email);
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
				.andExpect(jsonPath("$.data.user.displayName").value("New User"))
				.andExpect(jsonPath("$.data.user.emailVerified").value(false));

		Integer roleCount = jdbcTemplate.queryForObject("""
				select count(*)
				from user_roles ur
				join users u on u.id = ur.user_id
				where u.email = ? and ur.role = ?
				""", Integer.class, "new.user@example.com", "ROLE_USER");
		assertThat(roleCount).isEqualTo(1);

		verify(valueOperations).set(
				eq("email-verification:new.user@example.com"),
				matches("\\d{6}"),
				eq(Duration.ofMinutes(10)));

		mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"new.user@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void signupUsesVietnameseMessageWhenRequested() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.header("Accept-Language", "vi")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"signup-vi@example.com","password":"Password123!","displayName":"Signup Vi"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Đăng ký thành công"))
				.andExpect(jsonPath("$.data.user.email").value("signup-vi@example.com"));
	}

	@Test
	void verifyEmailActivatesUserAndAllowsSignin() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"verify@example.com","password":"Password123!","displayName":"Verify User"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.emailVerified").value(false));

		String otp = captureSignupOtp("verify@example.com");
		verifyEmail("verify@example.com", otp);

		verify(redisTemplate).delete("email-verification:verify@example.com");

		Boolean verified = jdbcTemplate.queryForObject(
				"select enabled and email_verified from users where email = ?",
				Boolean.class,
				"verify@example.com");
		assertThat(verified).isTrue();

		mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"verify@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Signin successful"))
				.andExpect(jsonPath("$.data.user.email").value("verify@example.com"))
				.andExpect(jsonPath("$.data.user.emailVerified").value(true));
	}

	@Test
	void verifyEmailRejectsInvalidOtp() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"invalid-otp@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		when(valueOperations.get("email-verification:invalid-otp@example.com")).thenReturn("123456");

		mockMvc().perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"invalid-otp@example.com","otp":"654321"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid or expired email verification OTP"));
	}

	@Test
	void resendVerificationOtpIssuesNewOtpForUnverifiedUser() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"resend@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		when(valueOperations.setIfAbsent(
				eq("email-verification-resend:resend@example.com"),
				eq("1"),
				eq(Duration.ofSeconds(60)))).thenReturn(true);

		mockMvc().perform(post("/api/v1/auth/resend-verification-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"resend@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Verification OTP resent"))
				.andExpect(jsonPath("$.data.resendCooldownSeconds").value(60));

		List<String> issuedOtps = captureIssuedOtps("resend@example.com", 2);
		assertThat(issuedOtps).allMatch(otp -> otp.matches("\\d{6}"));
	}

	@Test
	void resendVerificationOtpRejectsRequestsDuringCooldown() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"cooldown@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		when(valueOperations.setIfAbsent(
				eq("email-verification-resend:cooldown@example.com"),
				eq("1"),
				eq(Duration.ofSeconds(60)))).thenReturn(false);
		when(redisTemplate.getExpire("email-verification-resend:cooldown@example.com", TimeUnit.SECONDS))
				.thenReturn(42L);

		mockMvc().perform(post("/api/v1/auth/resend-verification-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"cooldown@example.com"}
								"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "42"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Please wait 42 seconds before requesting another verification OTP"));

		captureIssuedOtps("cooldown@example.com", 1);
	}

	@Test
	void resendVerificationOtpCooldownUsesVietnameseMessageWhenRequested() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"cooldown-vi@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		when(valueOperations.setIfAbsent(
				eq("email-verification-resend:cooldown-vi@example.com"),
				eq("1"),
				eq(Duration.ofSeconds(60)))).thenReturn(false);
		when(redisTemplate.getExpire("email-verification-resend:cooldown-vi@example.com", TimeUnit.SECONDS))
				.thenReturn(42L);

		mockMvc().perform(post("/api/v1/auth/resend-verification-otp")
						.header("Accept-Language", "vi")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"cooldown-vi@example.com"}
								"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "42"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Vui lòng chờ 42 giây trước khi yêu cầu mã OTP xác minh mới"));
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
	void duplicateEmailUsesVietnameseMessageWhenRequested() throws Exception {
		String body = """
				{"email":"duplicate-vi@example.com","password":"Password123!","displayName":"Duplicate Vi"}
				""";

		mockMvc().perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/auth/signup")
						.header("Accept-Language", "vi")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Email đã được đăng ký"));
	}

	@Test
	void signinReturnsTokenForValidCredentials() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"signin@example.com","password":"Password123!","displayName":"Signin User"}
								"""))
				.andExpect(status().isOk());
		verifyEmail("signin@example.com", captureSignupOtp("signin@example.com"));

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
	void signinIssuesRefreshTokenAndStoresOnlyItsHash() throws Exception {
		signupAndVerify("signin-refresh@example.com");

		MvcResult signin = signin("signin-refresh@example.com", "Password123!");

		String refreshToken = refreshTokenFrom(signin);
		assertThat(refreshToken).isNotBlank();
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from refresh_tokens rt
				join users u on u.id = rt.user_id
				where u.email = ?
				  and rt.revoked = false
				  and rt.token_hash <> ?
				""", Integer.class, "signin-refresh@example.com", refreshToken)).isGreaterThanOrEqualTo(1);
	}

	@Test
	void logoutRevokesCurrentRefreshTokenOnlyOnce() throws Exception {
		signupAndVerify("logout-current@example.com");
		MvcResult firstSignin = signin("logout-current@example.com", "Password123!");
		MvcResult secondSignin = signin("logout-current@example.com", "Password123!");
		String accessToken = tokenFrom(secondSignin);
		String refreshToken = refreshTokenFrom(secondSignin);
		int activeBeforeLogout = activeRefreshTokenCount("logout-current@example.com");
		assertThat(activeBeforeLogout).isGreaterThanOrEqualTo(2);

		mockMvc().perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Logout successful"));

		assertThat(activeRefreshTokenCount("logout-current@example.com")).isEqualTo(activeBeforeLogout - 1);

		mockMvc().perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer " + tokenFrom(firstSignin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Logout successful"));

		assertThat(activeRefreshTokenCount("logout-current@example.com")).isEqualTo(activeBeforeLogout - 1);
	}

	@Test
	void logoutAllDevicesRevokesOnlyAuthenticatedUsersActiveRefreshTokens() throws Exception {
		signupAndVerify("logout-all@example.com");
		signupAndVerify("logout-all-other@example.com");
		MvcResult firstSignin = signin("logout-all@example.com", "Password123!");
		signin("logout-all@example.com", "Password123!");
		signin("logout-all-other@example.com", "Password123!");
		int otherUsersActiveTokens = activeRefreshTokenCount("logout-all-other@example.com");
		assertThat(activeRefreshTokenCount("logout-all@example.com")).isGreaterThanOrEqualTo(2);

		mockMvc().perform(post("/api/v1/auth/logout-all-devices")
						.header("Authorization", "Bearer " + tokenFrom(firstSignin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Logged out from all devices"));

		assertThat(activeRefreshTokenCount("logout-all@example.com")).isZero();
		assertThat(activeRefreshTokenCount("logout-all-other@example.com")).isEqualTo(otherUsersActiveTokens);
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
	void signinRejectsEnabledUserWithoutVerifiedEmail() throws Exception {
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"enabled-unverified@example.com","password":"Password123!"}
								"""))
				.andExpect(status().isOk());

		jdbcTemplate.update(
				"update users set enabled = true, email_verified = false where email = ?",
				"enabled-unverified@example.com");

		mockMvc().perform(post("/api/v1/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"enabled-unverified@example.com","password":"Password123!"}
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
		verifyEmail("me@example.com", captureSignupOtp("me@example.com"));

		String token = tokenFrom(signup);

		mockMvc().perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Current user"))
				.andExpect(jsonPath("$.data.email").value("me@example.com"))
				.andExpect(jsonPath("$.data.displayName").value("Me User"));
	}

	@Test
	void usersMeReturnsCurrentUserForBearerToken() throws Exception {
		MvcResult signup = mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"users-me@example.com","password":"Password123!","displayName":"Users Me"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		verifyEmail("users-me@example.com", captureSignupOtp("users-me@example.com"));

		String token = tokenFrom(signup);

		mockMvc().perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Current user"))
				.andExpect(jsonPath("$.data.email").value("users-me@example.com"))
				.andExpect(jsonPath("$.data.displayName").value("Users Me"))
				.andExpect(jsonPath("$.data.emailVerified").value(true));
	}

	@Test
	void protectedEndpointRejectsInvalidBearerToken() throws Exception {
		mockMvc().perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-valid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}
}
