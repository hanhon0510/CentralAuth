package com.centralauth.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class RefreshTokenMapperIntegrationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:refresh-token-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USERS;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.data.redis.host", () -> "localhost");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("centralauth.jwt.secret", () -> "test-secret-with-at-least-32-characters");
	}

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RefreshTokenMapper refreshTokenMapper;

	@Test
	void insertAndFindByTokenHashPersistsRefreshTokenMetadata() {
		String userId = createUser("refresh-token@example.com");
		RefreshToken refreshToken = new RefreshToken(
				UUID.randomUUID().toString(),
				userId,
				"sha256:stored-token-hash",
				Instant.parse("2026-05-13T10:00:00Z"),
				Instant.parse("2026-06-12T10:00:00Z"),
				false,
				null,
				null,
				null);

		refreshTokenMapper.insert(refreshToken);

		RefreshToken saved = refreshTokenMapper.findByTokenHash("sha256:stored-token-hash").orElseThrow();
		assertThat(saved.id()).isEqualTo(refreshToken.id());
		assertThat(saved.userId()).isEqualTo(userId);
		assertThat(saved.tokenHash()).isEqualTo("sha256:stored-token-hash");
		assertThat(saved.issuedAt()).isEqualTo(refreshToken.issuedAt());
		assertThat(saved.expiresAt()).isEqualTo(refreshToken.expiresAt());
		assertThat(saved.revoked()).isFalse();
		assertThat(saved.revokedAt()).isNull();
	}

	@Test
	void findByUserIdReturnsRefreshTokensForUser() {
		String userId = createUser("refresh-token-list@example.com");
		RefreshToken refreshToken = new RefreshToken(
				UUID.randomUUID().toString(),
				userId,
				"sha256:user-token-hash",
				Instant.parse("2026-05-13T10:00:00Z"),
				Instant.parse("2026-06-12T10:00:00Z"),
				false,
				null,
				null,
				null);
		refreshTokenMapper.insert(refreshToken);

		List<RefreshToken> tokens = refreshTokenMapper.findByUserId(userId);

		assertThat(tokens).extracting(RefreshToken::tokenHash).contains("sha256:user-token-hash");
	}

	@Test
	void revokeMarksRefreshTokenRevokedWithTimestamp() {
		String userId = createUser("refresh-token-revoke@example.com");
		RefreshToken refreshToken = new RefreshToken(
				UUID.randomUUID().toString(),
				userId,
				"sha256:revoked-token-hash",
				Instant.parse("2026-05-13T10:00:00Z"),
				Instant.parse("2026-06-12T10:00:00Z"),
				false,
				null,
				null,
				null);
		refreshTokenMapper.insert(refreshToken);

		int updated = refreshTokenMapper.revoke(refreshToken.id(), Instant.parse("2026-05-14T10:00:00Z"));

		assertThat(updated).isEqualTo(1);
		RefreshToken saved = refreshTokenMapper.findByTokenHash("sha256:revoked-token-hash").orElseThrow();
		assertThat(saved.revoked()).isTrue();
		assertThat(saved.revokedAt()).isEqualTo(Instant.parse("2026-05-14T10:00:00Z"));
	}

	private String createUser(String email) {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update("""
				insert into users (id, email, password_hash, enabled, email_verified)
				values (?, ?, ?, true, true)
				""", userId, email, "password-hash");
		return userId.toString();
	}
}
