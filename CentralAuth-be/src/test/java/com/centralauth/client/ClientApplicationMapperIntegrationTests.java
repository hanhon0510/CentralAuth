package com.centralauth.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ClientApplicationMapperIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:client-mapper-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("centralauth.kafka.audit.enabled", () -> "false");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	ClientApplicationMapper clientApplicationMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearClients() {
		jdbcTemplate.execute("delete from clients");
	}

	@Test
	void insertFindAndListClientApplicationWithRedirectUrisAndAllowedOrigins() {
		ClientApplication client = new ClientApplication(
				"dashboard-app",
				"Dashboard App",
				true,
				null,
				null,
				List.of(
						"https://dashboard.example.com/auth/callback",
						"http://localhost:5173/auth/callback"),
				List.of(
						"https://dashboard.example.com",
						"http://localhost:5173"));

		clientApplicationMapper.insert(client);
		client.redirectUris().forEach(uri -> clientApplicationMapper.insertRedirectUri(client.clientId(), uri));
		client.allowedOrigins().forEach(origin -> clientApplicationMapper.insertAllowedOrigin(client.clientId(), origin));

		ClientApplication saved = clientApplicationMapper.findByClientId("dashboard-app").orElseThrow();

		assertThat(saved.clientId()).isEqualTo("dashboard-app");
		assertThat(saved.clientName()).isEqualTo("Dashboard App");
		assertThat(saved.active()).isTrue();
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(saved.redirectUris()).containsExactly(
				"http://localhost:5173/auth/callback",
				"https://dashboard.example.com/auth/callback");
		assertThat(saved.allowedOrigins()).containsExactly(
				"http://localhost:5173",
				"https://dashboard.example.com");

		Instant originalCreatedAt = saved.createdAt();

		int updated = clientApplicationMapper.updateClient("dashboard-app", "Dashboard Console", false);
		clientApplicationMapper.deleteRedirectUris("dashboard-app");
		clientApplicationMapper.insertRedirectUri("dashboard-app", "https://console.example.com/callback");
		clientApplicationMapper.deleteAllowedOrigins("dashboard-app");
		clientApplicationMapper.insertAllowedOrigin("dashboard-app", "https://console.example.com");

		assertThat(updated).isEqualTo(1);

		ClientApplication replaced = clientApplicationMapper.findByClientId("dashboard-app").orElseThrow();
		assertThat(replaced.clientName()).isEqualTo("Dashboard Console");
		assertThat(replaced.active()).isFalse();
		assertThat(replaced.createdAt()).isEqualTo(originalCreatedAt);
		assertThat(replaced.redirectUris()).containsExactly("https://console.example.com/callback");
		assertThat(replaced.allowedOrigins()).containsExactly("https://console.example.com");

		clientApplicationMapper.insert(new ClientApplication(
				"mobile-app",
				"Mobile App",
				true,
				null,
				null,
				List.of(),
				List.of()));

		assertThat(clientApplicationMapper.updateActive("dashboard-app", true)).isEqualTo(1);

		List<ClientApplication> clients = clientApplicationMapper.findAll();

		assertThat(clients).extracting(ClientApplication::clientId)
				.containsExactly("mobile-app", "dashboard-app");
		assertThat(clientApplicationMapper.findByClientId("dashboard-app").orElseThrow().active()).isTrue();
	}
}
