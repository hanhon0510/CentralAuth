package com.centralauth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.centralauth.security.JwtPrincipal;
import com.centralauth.security.JwtService;
import com.centralauth.email.AuthEmailService;

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
		registry.add("centralauth.kafka.enabled", () -> "true");
		registry.add("centralauth.kafka.audit.enabled", () -> "false");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtService jwtService;

	@MockitoBean
	StringRedisTemplate redisTemplate;

	@MockitoBean
	KafkaTemplate<String, Object> kafkaTemplate;

	@MockitoBean
	AuthEmailService authEmailService;

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
				alter table users
				add column if not exists account_status varchar(32) not null default 'UNVERIFIED'
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
		jdbcTemplate.execute("delete from clients");
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

	private List<String> captureSentVerificationOtps(String email, int count) {
		ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
		verify(authEmailService, times(count)).sendVerificationOtp(eq(email), otpCaptor.capture());
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

	private String userIdFor(String email) {
		return jdbcTemplate.queryForObject("select cast(id as varchar) from users where email = ?", String.class, email);
	}

	private String capturePasswordResetToken(String email) {
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
				keyCaptor.capture(),
				eq(userIdFor(email)),
				eq(Duration.ofMinutes(15)));
		String key = keyCaptor.getValue();
		assertThat(key).startsWith("password-reset:");
		String token = key.substring("password-reset:".length());
		assertThat(token).matches("[A-Za-z0-9_-]{43}");
		return token;
	}

	private Object captureKafkaEvent(String topic, String key) {
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(kafkaTemplate).send(eq(topic), eq(key), eventCaptor.capture());
		return eventCaptor.getValue();
	}

	private void assertOccurredAt(Object event) {
		assertThat(event)
				.extracting("occurredAt")
				.isInstanceOf(Instant.class);
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
		return signinAttempt(email, password, "127.0.0.1")
				.andExpect(status().isOk())
				.andReturn();
	}

	private ResultActions signinAttempt(String email, String password, String clientIp) throws Exception {
		return mockMvc().perform(post("/api/v1/auth/signin")
						.header("X-Forwarded-For", clientIp)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)));
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

	private boolean refreshTokenRevoked(String refreshToken) throws Exception {
		return jdbcTemplate.queryForObject("""
				select revoked
				from refresh_tokens
				where token_hash = ?
				""", Boolean.class, hashRefreshToken(refreshToken));
	}

	private String hashRefreshToken(String token) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8));
		return "sha256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
	}

	private void insertClient(String clientId, String clientName, String redirectUri) {
		jdbcTemplate.update("""
				insert into clients (client_id, client_name, active)
				values (?, ?, true)
				""", clientId, clientName);
		jdbcTemplate.update("""
				insert into client_redirect_uris (client_id, redirect_uri)
				values (?, ?)
				""", clientId, redirectUri);
	}

	private void insertClientWithLogout(
			String clientId,
			String clientName,
			String redirectUri,
			String logoutUri,
			boolean active) {
		jdbcTemplate.update("""
				insert into clients (client_id, client_name, active)
				values (?, ?, ?)
				""", clientId, clientName, active);
		jdbcTemplate.update("""
				insert into client_redirect_uris (client_id, redirect_uri)
				values (?, ?)
				""", clientId, redirectUri);
		jdbcTemplate.update("""
				insert into client_logout_uris (client_id, logout_uri)
				values (?, ?)
				""", clientId, logoutUri);
	}

	private String captureCentralLoginStateContext(
			String loginState,
			String clientId,
			String redirectUri,
			String state) {
		ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
				eq("auth_state:" + loginState),
				contextCaptor.capture(),
				eq(Duration.ofMinutes(10)));
		assertThat(contextCaptor.getValue())
				.contains("\"clientId\":\"" + clientId + "\"")
				.contains("\"redirectUri\":\"" + redirectUri + "\"")
				.contains("\"clientState\":\"" + state + "\"");
		return contextCaptor.getValue();
	}

	private String captureCentralLoginCodeContext(String code, String email, String clientId, String redirectUri) {
		ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
				eq("auth_code:" + code),
				contextCaptor.capture(),
				eq(Duration.ofMinutes(5)));
		assertThat(contextCaptor.getValue())
				.contains("\"userId\":\"" + userIdFor(email) + "\"")
				.contains("\"clientId\":\"" + clientId + "\"")
				.contains("\"redirectUri\":\"" + redirectUri + "\"");
		return contextCaptor.getValue();
	}

	private String issueCentralLoginState(String clientId, String redirectUri, String state) throws Exception {
		MvcResult result = mockMvc().perform(get("/api/v1/auth/central-login/context")
						.param("client_id", clientId)
						.param("redirect_uri", redirectUri)
						.param("state", state))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.loginState", not(blankOrNullString())))
				.andReturn();
		String loginState = stringFieldFrom(result, "loginState");
		String stateContext = captureCentralLoginStateContext(loginState, clientId, redirectUri, state);
		when(valueOperations.get("auth_state:" + loginState)).thenReturn(stateContext);
		when(valueOperations.getAndDelete("auth_state:" + loginState)).thenReturn(stateContext);
		return loginState;
	}

	@Test
	void centralLoginContextValidatesClientAndReturnsDisplayMetadata() throws Exception {
		insertClient(
				"dashboard-app",
				"Dashboard App",
				"https://dashboard.example.com/auth/callback");

		MvcResult result = mockMvc().perform(get("/api/v1/auth/central-login/context")
						.param("client_id", "dashboard-app")
						.param("redirect_uri", "https://dashboard.example.com/auth/callback")
						.param("state", "client-state-123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Central login context"))
				.andExpect(jsonPath("$.data.clientId").value("dashboard-app"))
				.andExpect(jsonPath("$.data.clientName").value("Dashboard App"))
				.andExpect(jsonPath("$.data.redirectUri").value("https://dashboard.example.com/auth/callback"))
				.andExpect(jsonPath("$.data.state").value("client-state-123"))
				.andExpect(jsonPath("$.data.loginState", not(blankOrNullString())))
				.andReturn();

		captureCentralLoginStateContext(
				stringFieldFrom(result, "loginState"),
				"dashboard-app",
				"https://dashboard.example.com/auth/callback",
				"client-state-123");
	}

	@Test
	void centralLoginContextRejectsMismatchedRedirectUri() throws Exception {
		insertClient(
				"mismatched-app",
				"Mismatched App",
				"https://dashboard.example.com/auth/callback");

		mockMvc().perform(get("/api/v1/auth/central-login/context")
						.param("client_id", "mismatched-app")
						.param("redirect_uri", "https://evil.example.com/auth/callback"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid client metadata"));
	}

	@Test
	void centralLoginSigninReturnsRedirectCodeAndStoresTemporaryContext() throws Exception {
		String email = "central-login@example.com";
		String redirectUri = "https://dashboard.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("central-dashboard", "Central Dashboard", redirectUri);
		String loginState = issueCentralLoginState("central-dashboard", redirectUri, "state-after-login");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult result = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.80")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"central-dashboard",
								  "redirectUri":"%s",
								  "state":"state-after-login",
								  "loginState":"%s"
								}
								""".formatted(email, redirectUri, loginState)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Central login successful"))
				.andExpect(jsonPath("$.data.redirectUri").value(redirectUri))
				.andExpect(jsonPath("$.data.code", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.state").value("state-after-login"))
				.andExpect(jsonPath("$.data.redirectUrl", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.auth.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.auth.refreshToken", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.auth.user.email").value(email))
				.andReturn();

		String code = stringFieldFrom(result, "code");
		assertThat(stringFieldFrom(result, "redirectUrl"))
				.startsWith(redirectUri + "?code=" + code)
				.contains("state=state-after-login");
		captureCentralLoginCodeContext(
				code,
				email,
				"central-dashboard",
				redirectUri);
		verify(valueOperations).get("auth_state:" + loginState);
		verify(valueOperations).getAndDelete("auth_state:" + loginState);
	}

	@Test
	void centralLoginSigninIssuesCentralSessionThatCanContinueSecondClientFlow() throws Exception {
		String email = "central-sso@example.com";
		String firstRedirectUri = "https://projects.example.com/auth/callback";
		String secondRedirectUri = "https://reports.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("projects-client", "Projects Client", firstRedirectUri);
		insertClient("reports-client", "Reports Client", secondRedirectUri);
		String firstLoginState = issueCentralLoginState("projects-client", firstRedirectUri, "projects-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult firstLogin = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.84")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"projects-client",
								  "redirectUri":"%s",
								  "state":"projects-state",
								  "loginState":"%s"
								}
								""".formatted(email, firstRedirectUri, firstLoginState)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.auth.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.auth.refreshToken", not(blankOrNullString())))
				.andReturn();
		String centralSessionToken = stringFieldFrom(firstLogin, "token");

		String secondLoginState = issueCentralLoginState("reports-client", secondRedirectUri, "reports-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult secondLogin = mockMvc().perform(post("/api/v1/auth/central-login/continue")
						.header("Authorization", "Bearer " + centralSessionToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId":"reports-client",
								  "redirectUri":"%s",
								  "state":"reports-state",
								  "loginState":"%s"
								}
								""".formatted(secondRedirectUri, secondLoginState)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Central login successful"))
				.andExpect(jsonPath("$.data.redirectUri").value(secondRedirectUri))
				.andExpect(jsonPath("$.data.code", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.state").value("reports-state"))
				.andExpect(jsonPath("$.data.redirectUrl", not(blankOrNullString())))
				.andReturn();

		String secondCode = stringFieldFrom(secondLogin, "code");
		assertThat(stringFieldFrom(secondLogin, "redirectUrl"))
				.startsWith(secondRedirectUri + "?code=" + secondCode)
				.contains("state=reports-state");
		captureCentralLoginCodeContext(
				secondCode,
				email,
				"reports-client",
				secondRedirectUri);
		verify(valueOperations).get("auth_state:" + secondLoginState);
		verify(valueOperations).getAndDelete("auth_state:" + secondLoginState);
	}

	@Test
	void centralLoginContinueReturnsRedirectCodeForCurrentSession() throws Exception {
		String email = "central-continue@example.com";
		String redirectUri = "https://console.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("central-console", "Central Console", redirectUri);
		MvcResult signin = signin(email, "Password123!");
		String loginState = issueCentralLoginState("central-console", redirectUri, "existing-session-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult result = mockMvc().perform(post("/api/v1/auth/central-login/continue")
						.header("Authorization", "Bearer " + tokenFrom(signin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientId":"central-console",
								  "redirectUri":"%s",
								  "state":"existing-session-state",
								  "loginState":"%s"
								}
								""".formatted(redirectUri, loginState)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Central login successful"))
				.andExpect(jsonPath("$.data.redirectUri").value(redirectUri))
				.andExpect(jsonPath("$.data.code", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.state").value("existing-session-state"))
				.andExpect(jsonPath("$.data.redirectUrl", not(blankOrNullString())))
				.andReturn();

		String code = stringFieldFrom(result, "code");
		assertThat(stringFieldFrom(result, "redirectUrl"))
				.startsWith(redirectUri + "?code=" + code)
				.contains("state=existing-session-state");
		captureCentralLoginCodeContext(
				code,
				email,
				"central-console",
				redirectUri);
		verify(valueOperations).get("auth_state:" + loginState);
		verify(valueOperations).getAndDelete("auth_state:" + loginState);
	}

	@Test
	void centralLoginCodeExchangeConsumesCodeAndReturnsTokens() throws Exception {
		String email = "central-exchange@example.com";
		String redirectUri = "https://exchange.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("exchange-client", "Exchange Client", redirectUri);
		String loginState = issueCentralLoginState("exchange-client", redirectUri, "exchange-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult login = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.81")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"exchange-client",
								  "redirectUri":"%s",
								  "state":"exchange-state",
								  "loginState":"%s"
								}
								""".formatted(email, redirectUri, loginState)))
				.andExpect(status().isOk())
				.andReturn();
		String code = stringFieldFrom(login, "code");
		String codeContext = captureCentralLoginCodeContext(code, email, "exchange-client", redirectUri);
		when(valueOperations.getAndDelete("auth_code:" + code)).thenReturn(codeContext);
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult exchange = mockMvc().perform(post("/api/v1/auth/central-login/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code":"%s",
								  "clientId":"exchange-client",
								  "redirectUri":"%s"
								}
								""".formatted(code, redirectUri)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Central login token issued"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(jsonPath("$.data.user.email").value(email))
				.andReturn();

		mockMvc().perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + tokenFrom(exchange)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value(email));

		verify(valueOperations).getAndDelete("auth_code:" + code);
	}

	@Test
	void centralLoginClientTokenIsRejectedWhenClientBecomesInactive() throws Exception {
		String email = "central-exchange-inactive-client@example.com";
		String redirectUri = "https://inactive-exchange.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("inactive-exchange-client", "Inactive Exchange Client", redirectUri);
		String loginState = issueCentralLoginState("inactive-exchange-client", redirectUri, "inactive-exchange-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult login = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.85")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"inactive-exchange-client",
								  "redirectUri":"%s",
								  "state":"inactive-exchange-state",
								  "loginState":"%s"
								}
								""".formatted(email, redirectUri, loginState)))
				.andExpect(status().isOk())
				.andReturn();
		String code = stringFieldFrom(login, "code");
		String codeContext = captureCentralLoginCodeContext(code, email, "inactive-exchange-client", redirectUri);
		when(valueOperations.getAndDelete("auth_code:" + code)).thenReturn(codeContext);

		MvcResult exchange = mockMvc().perform(post("/api/v1/auth/central-login/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code":"%s",
								  "clientId":"inactive-exchange-client",
								  "redirectUri":"%s"
								}
								""".formatted(code, redirectUri)))
				.andExpect(status().isOk())
				.andReturn();

		jdbcTemplate.update("update clients set active = false where client_id = ?", "inactive-exchange-client");

		mockMvc().perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + tokenFrom(exchange)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void centralLoginCodeExchangeRejectsMismatchedRedirectUri() throws Exception {
		String email = "central-exchange-invalid@example.com";
		String redirectUri = "https://exchange-valid.example.com/auth/callback";
		signupAndVerify(email);
		insertClient("exchange-invalid-client", "Exchange Invalid Client", redirectUri);
		String loginState = issueCentralLoginState("exchange-invalid-client", redirectUri, "exchange-invalid-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult login = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.82")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"exchange-invalid-client",
								  "redirectUri":"%s",
								  "state":"exchange-invalid-state",
								  "loginState":"%s"
								}
								""".formatted(email, redirectUri, loginState)))
				.andExpect(status().isOk())
				.andReturn();
		String code = stringFieldFrom(login, "code");
		String codeContext = captureCentralLoginCodeContext(code, email, "exchange-invalid-client", redirectUri);
		when(valueOperations.getAndDelete("auth_code:" + code)).thenReturn(codeContext);

		mockMvc().perform(post("/api/v1/auth/central-login/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code":"%s",
								  "clientId":"exchange-invalid-client",
								  "redirectUri":"https://evil.example.com/auth/callback"
								}
								""".formatted(code)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid or expired authorization code"));
	}

	@Test
	void centralLoginCodeExchangeDoesNotIssueAdminCapableCentralAuthToken() throws Exception {
		String email = "central-exchange-admin@example.com";
		String redirectUri = "https://admin-client.example.com/auth/callback";
		signupAndVerify(email);
		jdbcTemplate.update(
				"insert into user_roles (user_id, role) values (cast(? as uuid), ?)",
				userIdFor(email),
				"ROLE_ADMIN");
		insertClient("admin-exchange-client", "Admin Exchange Client", redirectUri);
		String loginState = issueCentralLoginState("admin-exchange-client", redirectUri, "admin-exchange-state");
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		MvcResult login = mockMvc().perform(post("/api/v1/auth/central-login")
						.header("X-Forwarded-For", "203.0.113.83")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"Password123!",
								  "clientId":"admin-exchange-client",
								  "redirectUri":"%s",
								  "state":"admin-exchange-state",
								  "loginState":"%s"
								}
								""".formatted(email, redirectUri, loginState)))
				.andExpect(status().isOk())
				.andReturn();
		String code = stringFieldFrom(login, "code");
		String codeContext = captureCentralLoginCodeContext(code, email, "admin-exchange-client", redirectUri);
		when(valueOperations.getAndDelete("auth_code:" + code)).thenReturn(codeContext);

		MvcResult exchange = mockMvc().perform(post("/api/v1/auth/central-login/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code":"%s",
								  "clientId":"admin-exchange-client",
								  "redirectUri":"%s"
								}
								""".formatted(code, redirectUri)))
				.andExpect(status().isOk())
				.andReturn();
		String exchangedToken = tokenFrom(exchange);
		JwtPrincipal principal = jwtService.validate(exchangedToken).orElseThrow();
		assertThat(principal.centralAuthAccessToken()).isFalse();
		assertThat(principal.audience()).isEqualTo("admin-exchange-client");
		assertThat(principal.roles()).doesNotContain("ROLE_ADMIN");

		mockMvc().perform(get("/api/v1/admin/users")
						.header("Authorization", "Bearer " + exchangedToken))
				.andExpect(status().isUnauthorized());
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
		verify(authEmailService).sendVerificationOtp(eq("new.user@example.com"), matches("\\d{6}"));

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
	void signupPublishesUserRegisteredEvent() throws Exception {
		String email = "registered-event@example.com";

		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","displayName":"Registered Event"}
								""".formatted(email)))
				.andExpect(status().isOk());

		String userId = userIdFor(email);
		Object event = captureKafkaEvent("auth.user.registered", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("email", email)
				.hasFieldOrPropertyWithValue("displayName", "Registered Event");
		assertOccurredAt(event);
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
	void verifyEmailPublishesUserVerifiedEvent() throws Exception {
		String email = "verified-event@example.com";
		mockMvc().perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","displayName":"Verified Event"}
								""".formatted(email)))
				.andExpect(status().isOk());

		String otp = captureSignupOtp(email);
		String userId = userIdFor(email);
		clearInvocations(kafkaTemplate);

		verifyEmail(email, otp);

		Object event = captureKafkaEvent("auth.user.verified", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("email", email);
		assertOccurredAt(event);
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
		assertThat(captureSentVerificationOtps("resend@example.com", 2)).isEqualTo(issuedOtps);
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
	void forgotPasswordStoresResetTokenForExistingEnabledUser() throws Exception {
		signupAndVerify("forgot-existing@example.com");

		mockMvc().perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"forgot-existing@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message")
						.value("If the email is registered, password reset instructions have been sent"));

		String token = capturePasswordResetToken("forgot-existing@example.com");
		verify(authEmailService).sendPasswordResetLink(
				eq("forgot-existing@example.com"),
				eq(token));
	}

	@Test
	void forgotPasswordPublishesPasswordResetRequestedEventForExistingEnabledUser() throws Exception {
		String email = "reset-requested-event@example.com";
		signupAndVerify(email);
		String userId = userIdFor(email);
		clearInvocations(kafkaTemplate);

		mockMvc().perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(email)))
				.andExpect(status().isOk());

		Object event = captureKafkaEvent("auth.user.password.reset.requested", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("email", email);
		assertOccurredAt(event);
	}

	@Test
	void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
		mockMvc().perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"missing-reset@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message")
						.value("If the email is registered, password reset instructions have been sent"));

		verify(valueOperations, never()).set(
				matches("password-reset:.*"),
				anyString(),
				eq(Duration.ofMinutes(15)));
		verify(authEmailService, never()).sendPasswordResetLink(anyString(), anyString());
	}

	@Test
	void resetPasswordUpdatesPasswordAndRevokesActiveRefreshTokens() throws Exception {
		signupAndVerify("reset-valid@example.com");
		signin("reset-valid@example.com", "Password123!");
		signin("reset-valid@example.com", "Password123!");
		assertThat(activeRefreshTokenCount("reset-valid@example.com")).isGreaterThanOrEqualTo(2);

		mockMvc().perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"reset-valid@example.com"}
								"""))
				.andExpect(status().isOk());
		String token = capturePasswordResetToken("reset-valid@example.com");
		when(valueOperations.getAndDelete("password-reset:" + token)).thenReturn(userIdFor("reset-valid@example.com"));

		mockMvc().perform(post("/api/v1/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s","newPassword":"NewPassword123!"}
								""".formatted(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Password reset successful"));

		assertThat(activeRefreshTokenCount("reset-valid@example.com")).isZero();

		signinAttempt("reset-valid@example.com", "Password123!", "203.0.113.60")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		signinAttempt("reset-valid@example.com", "NewPassword123!", "203.0.113.61")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Signin successful"));
	}

	@Test
	void resetPasswordPublishesPasswordChangedEvent() throws Exception {
		String email = "password-changed-event@example.com";
		signupAndVerify(email);
		mockMvc().perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(email)))
				.andExpect(status().isOk());
		String userId = userIdFor(email);
		String token = capturePasswordResetToken(email);
		when(valueOperations.getAndDelete("password-reset:" + token)).thenReturn(userId);
		clearInvocations(kafkaTemplate);

		mockMvc().perform(post("/api/v1/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s","newPassword":"NewPassword123!"}
								""".formatted(token)))
				.andExpect(status().isOk());

		Object event = captureKafkaEvent("auth.user.password.changed", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("email", email);
		assertOccurredAt(event);
	}

	@Test
	void resetPasswordRejectsInvalidToken() throws Exception {
		when(valueOperations.getAndDelete("password-reset:not-a-reset-token")).thenReturn(null);

		mockMvc().perform(post("/api/v1/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"not-a-reset-token","newPassword":"NewPassword123!"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid or expired password reset token"));
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
	void signinTokenIncludesRolesFromDatabase() throws Exception {
		String email = "role-token@example.com";
		signupAndVerify(email);
		jdbcTemplate.update(
				"insert into user_roles (user_id, role) values (cast(? as uuid), ?)",
				userIdFor(email),
				"ROLE_ADMIN");

		MvcResult signin = signin(email, "Password123!");

		JwtPrincipal principal = jwtService.validate(tokenFrom(signin)).orElseThrow();
		assertThat(principal.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
	}

	@Test
	void signinPublishesLoginSucceededEvent() throws Exception {
		String email = "login-success-event@example.com";
		signupAndVerify(email);
		String userId = userIdFor(email);
		clearInvocations(kafkaTemplate);

		signinAttempt(email, "Password123!", "203.0.113.70")
				.andExpect(status().isOk());

		Object event = captureKafkaEvent("auth.user.login.succeeded", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("email", email)
				.hasFieldOrPropertyWithValue("clientIp", "203.0.113.70");
		assertOccurredAt(event);
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
	void refreshRotatesActiveRefreshToken() throws Exception {
		String email = "refresh-rotate@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String originalRefreshToken = refreshTokenFrom(signin);
		int activeBeforeRefresh = activeRefreshTokenCount(email);

		MvcResult refresh = mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(originalRefreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Session refreshed"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.user.email").value(email))
				.andReturn();

		String rotatedRefreshToken = refreshTokenFrom(refresh);
		assertThat(rotatedRefreshToken).isNotEqualTo(originalRefreshToken);
		assertThat(refreshTokenRevoked(originalRefreshToken)).isTrue();
		assertThat(refreshTokenRevoked(rotatedRefreshToken)).isFalse();
		assertThat(activeRefreshTokenCount(email)).isEqualTo(activeBeforeRefresh);
		JwtPrincipal principal = jwtService.validate(tokenFrom(refresh)).orElseThrow();
		assertThat(principal.userId()).isEqualTo(userIdFor(email));
	}

	@Test
	void refreshRejectsReusedRefreshToken() throws Exception {
		String email = "refresh-reuse@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String originalRefreshToken = refreshTokenFrom(signin);

		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(originalRefreshToken)))
				.andExpect(status().isOk());

		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(originalRefreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
	}

	@Test
	void refreshTokenReuseRevokesRemainingActiveTokensForUser() throws Exception {
		String email = "refresh-reuse-revokes@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String originalRefreshToken = refreshTokenFrom(signin);
		MvcResult refresh = mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(originalRefreshToken)))
				.andExpect(status().isOk())
				.andReturn();
		String rotatedRefreshToken = refreshTokenFrom(refresh);

		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(originalRefreshToken)))
				.andExpect(status().isUnauthorized());

		assertThat(activeRefreshTokenCount(email)).isZero();
		assertThat(refreshTokenRevoked(rotatedRefreshToken)).isTrue();
	}

	@Test
	void refreshRejectsInactiveOrMissingUsers() throws Exception {
		assertRefreshRejectedAfterAccountStatusChange("refresh-disabled@example.com", "DISABLED");
		assertRefreshRejectedAfterAccountStatusChange("refresh-locked@example.com", "LOCKED");
		assertRefreshRejectedAfterAccountStatusChange("refresh-unverified@example.com", "UNVERIFIED");

		String deletedEmail = "refresh-deleted@example.com";
		signupAndVerify(deletedEmail);
		MvcResult signin = signin(deletedEmail, "Password123!");
		String refreshToken = refreshTokenFrom(signin);
		jdbcTemplate.update("delete from users where email = ?", deletedEmail);

		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
	}

	@Test
	void refreshRejectsBlankRefreshToken() throws Exception {
		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":" "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid request"));
	}

	private void assertRefreshRejectedAfterAccountStatusChange(String email, String accountStatus) throws Exception {
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String refreshToken = refreshTokenFrom(signin);
		jdbcTemplate.update(
				"update users set account_status = ? where email = ?",
				accountStatus,
				email);

		mockMvc().perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
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
	void logoutReturnsActiveRegisteredClientLogoutUris() throws Exception {
		String email = "logout-front-channel@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String accessToken = tokenFrom(signin);
		String refreshToken = refreshTokenFrom(signin);
		insertClientWithLogout(
				"logout-projects-client",
				"Logout Projects Client",
				"https://projects.example.com/auth/callback",
				"https://projects.example.com/logout",
				true);
		insertClientWithLogout(
				"logout-reports-client",
				"Logout Reports Client",
				"https://reports.example.com/auth/callback",
				"https://reports.example.com/logout",
				true);
		insertClientWithLogout(
				"logout-inactive-client",
				"Logout Inactive Client",
				"https://inactive.example.com/auth/callback",
				"https://inactive.example.com/logout",
				false);

		mockMvc().perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.logoutUris[*]", containsInAnyOrder(
						"https://projects.example.com/logout",
						"https://reports.example.com/logout")));
	}

	@Test
	void logoutPublishesLogoutEvent() throws Exception {
		String email = "logout-event@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String accessToken = tokenFrom(signin);
		String refreshToken = refreshTokenFrom(signin);
		String userId = userIdFor(email);
		clearInvocations(kafkaTemplate);

		mockMvc().perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isOk());

		Object event = captureKafkaEvent("auth.user.logout", userId);
		assertThat(event)
				.hasFieldOrPropertyWithValue("userId", userId)
				.hasFieldOrPropertyWithValue("allDevices", false);
		assertOccurredAt(event);
	}

	@Test
	void logoutBlacklistsCurrentCentralAccessTokenUntilExpiry() throws Exception {
		String email = "logout-blacklist@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		String accessToken = tokenFrom(signin);
		String refreshToken = refreshTokenFrom(signin);
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		mockMvc().perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		ArgumentCaptor<String> blacklistKeyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
		verify(valueOperations).set(blacklistKeyCaptor.capture(), eq("1"), ttlCaptor.capture());
		assertThat(blacklistKeyCaptor.getValue()).startsWith("jwt:blacklist:");
		assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
		when(redisTemplate.hasKey(blacklistKeyCaptor.getValue())).thenReturn(true);

		mockMvc().perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
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
	void logoutAllDevicesReturnsActiveRegisteredClientLogoutUris() throws Exception {
		String email = "logout-all-front-channel@example.com";
		signupAndVerify(email);
		MvcResult signin = signin(email, "Password123!");
		insertClientWithLogout(
				"logout-all-projects-client",
				"Logout All Projects Client",
				"https://projects-all.example.com/auth/callback",
				"https://projects-all.example.com/logout",
				true);
		insertClientWithLogout(
				"logout-all-inactive-client",
				"Logout All Inactive Client",
				"https://inactive-all.example.com/auth/callback",
				"https://inactive-all.example.com/logout",
				false);

		mockMvc().perform(post("/api/v1/auth/logout-all-devices")
						.header("Authorization", "Bearer " + tokenFrom(signin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.logoutUris[0]").value("https://projects-all.example.com/logout"));
	}

	@Test
	void logoutAllDevicesRejectsPreviouslyIssuedCentralAccessTokens() throws Exception {
		String email = "logout-all-cutoff@example.com";
		signupAndVerify(email);
		MvcResult firstSignin = signin(email, "Password123!");
		MvcResult secondSignin = signin(email, "Password123!");
		String firstAccessToken = tokenFrom(firstSignin);
		String secondAccessToken = tokenFrom(secondSignin);
		String userId = userIdFor(email);
		clearInvocations(valueOperations, redisTemplate, kafkaTemplate);

		mockMvc().perform(post("/api/v1/auth/logout-all-devices")
						.header("Authorization", "Bearer " + firstAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		ArgumentCaptor<String> cutoffCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
				eq("jwt:user-logout-after:" + userId),
				cutoffCaptor.capture(),
				any(Duration.class));
		assertThat(cutoffCaptor.getValue()).matches("\\d+");
		when(valueOperations.get("jwt:user-logout-after:" + userId)).thenReturn(cutoffCaptor.getValue());

		mockMvc().perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + secondAccessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
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
	void signinPublishesLoginFailedEventForInvalidCredentials() throws Exception {
		String email = "login-failure-event@example.com";
		signupAndVerify(email);
		clearInvocations(kafkaTemplate);

		signinAttempt(email, "wrong-password", "203.0.113.71")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		Object event = captureKafkaEvent("auth.user.login.failed", email);
		assertThat(event)
				.hasFieldOrPropertyWithValue("email", email)
				.hasFieldOrPropertyWithValue("clientIp", "203.0.113.71")
				.hasFieldOrPropertyWithValue("reason", "INVALID_CREDENTIALS");
		assertOccurredAt(event);
	}

	@Test
	void signinRateLimitsEmailWithinShortWindow() throws Exception {
		signupAndVerify("rate-limit@example.com");
		when(valueOperations.increment("login-rate:email:rate-limit@example.com"))
				.thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
		when(redisTemplate.getExpire("login-rate:email:rate-limit@example.com", TimeUnit.SECONDS))
				.thenReturn(45L);

		for (int index = 1; index <= 10; index++) {
			String clientIp = "203.0.113." + index;
			when(valueOperations.increment("login-rate:ip:" + clientIp)).thenReturn(1L);

			signinAttempt("rate-limit@example.com", "Password123!", clientIp)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.message").value("Signin successful"));
		}
		when(valueOperations.increment("login-rate:ip:203.0.113.11")).thenReturn(1L);

		signinAttempt("rate-limit@example.com", "Password123!", "203.0.113.11")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "45"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Too many login attempts. Please try again in 45 seconds"));

		verify(valueOperations, times(11)).increment("login-rate:email:rate-limit@example.com");
		verify(redisTemplate).expire("login-rate:email:rate-limit@example.com", Duration.ofMinutes(1));
	}

	@Test
	void signinClearsTrackedFailuresAfterSuccessfulLogin() throws Exception {
		signupAndVerify("tracked-success@example.com");
		when(redisTemplate.getExpire("login-lock:email:tracked-success@example.com", TimeUnit.SECONDS))
				.thenReturn(-2L);
		when(redisTemplate.getExpire("login-lock:ip:203.0.113.50", TimeUnit.SECONDS))
				.thenReturn(-2L);
		when(valueOperations.increment("login-failure:email:tracked-success@example.com")).thenReturn(1L);
		when(valueOperations.increment("login-failure:ip:203.0.113.50")).thenReturn(1L);

		signinAttempt("tracked-success@example.com", "wrong-password", "203.0.113.50")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		signinAttempt("tracked-success@example.com", "Password123!", "203.0.113.50")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Signin successful"));

		verify(valueOperations).increment("login-failure:email:tracked-success@example.com");
		verify(valueOperations).increment("login-failure:ip:203.0.113.50");
		verify(redisTemplate).delete(List.of(
				"login-failure:email:tracked-success@example.com",
				"login-failure:ip:203.0.113.50"));
	}

	@Test
	void signinTemporarilyLocksEmailAfterTooManyFailedAttempts() throws Exception {
		signupAndVerify("email-lock@example.com");
		when(redisTemplate.getExpire("login-lock:email:email-lock@example.com", TimeUnit.SECONDS))
				.thenReturn(-2L, -2L, -2L, -2L, -2L, 900L);
		when(valueOperations.increment("login-failure:email:email-lock@example.com"))
				.thenReturn(1L, 2L, 3L, 4L, 5L);
		for (String clientIp : Arrays.asList(
				"203.0.113.1",
				"203.0.113.2",
				"203.0.113.3",
				"203.0.113.4",
				"203.0.113.5")) {
			when(redisTemplate.getExpire("login-lock:ip:" + clientIp, TimeUnit.SECONDS))
					.thenReturn(-2L);
			when(valueOperations.increment("login-failure:ip:" + clientIp)).thenReturn(1L);

			signinAttempt("email-lock@example.com", "wrong-password", clientIp)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message").value("Invalid email or password"));
		}
		when(redisTemplate.getExpire("login-lock:ip:203.0.113.99", TimeUnit.SECONDS))
				.thenReturn(-2L);

		signinAttempt("email-lock@example.com", "Password123!", "203.0.113.99")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "900"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Too many failed login attempts. Please try again in 900 seconds"));

		verify(valueOperations, times(5)).increment("login-failure:email:email-lock@example.com");
		verify(valueOperations).set(
				eq("login-lock:email:email-lock@example.com"),
				eq("1"),
				eq(Duration.ofMinutes(15)));
	}

	@Test
	void signinTemporarilyLocksIpAfterTooManyFailedAttempts() throws Exception {
		String lockedIp = "198.51.100.20";
		when(redisTemplate.getExpire("login-lock:ip:" + lockedIp, TimeUnit.SECONDS))
				.thenReturn(-2L, -2L, -2L, -2L, -2L, 600L);
		when(valueOperations.increment("login-failure:ip:" + lockedIp))
				.thenReturn(1L, 2L, 3L, 4L, 5L);

		for (int index = 1; index <= 5; index++) {
			String email = "ip-lock-" + index + "@example.com";
			signupAndVerify(email);
			when(redisTemplate.getExpire("login-lock:email:" + email, TimeUnit.SECONDS))
					.thenReturn(-2L);
			when(valueOperations.increment("login-failure:email:" + email)).thenReturn(1L);

			signinAttempt(email, "wrong-password", lockedIp)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message").value("Invalid email or password"));
		}
		signupAndVerify("ip-lock-valid@example.com");
		when(redisTemplate.getExpire("login-lock:email:ip-lock-valid@example.com", TimeUnit.SECONDS))
				.thenReturn(-2L);

		signinAttempt("ip-lock-valid@example.com", "Password123!", lockedIp)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "600"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Too many failed login attempts. Please try again in 600 seconds"));

		verify(valueOperations, times(5)).increment("login-failure:ip:" + lockedIp);
		verify(valueOperations).set(
				eq("login-lock:ip:" + lockedIp),
				eq("1"),
				eq(Duration.ofMinutes(15)));
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
	void signinRejectsLockedAccountEvenWhenLegacyFlagsAreEnabled() throws Exception {
		signupAndVerify("locked-status@example.com");
		jdbcTemplate.update(
				"update users set account_status = 'LOCKED', enabled = true, email_verified = true where email = ?",
				"locked-status@example.com");

		signinAttempt("locked-status@example.com", "Password123!", "203.0.113.72")
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
