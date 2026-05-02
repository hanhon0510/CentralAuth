package com.centralauth.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTests {

	@Test
	void usersMigrationDefinesAuthenticationTable() throws Exception {
		ClassPathResource migration = new ClassPathResource("db/migration/V1__create_users_table.sql");

		assertThat(migration.exists()).isTrue();

		String sql = migration.getContentAsString(StandardCharsets.UTF_8).toLowerCase();

		assertThat(sql).contains("create table users");
		assertThat(sql).contains("id uuid primary key");
		assertThat(sql).contains("email varchar(320) not null");
		assertThat(sql).contains("password_hash varchar(255) not null");
		assertThat(sql).contains("enabled boolean not null default true");
		assertThat(sql).contains("created_at timestamp with time zone not null default current_timestamp");
		assertThat(sql).contains("constraint users_email_key unique (email)");
	}
}
