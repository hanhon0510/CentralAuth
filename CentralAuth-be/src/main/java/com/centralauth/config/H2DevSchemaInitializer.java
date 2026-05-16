package com.centralauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class H2DevSchemaInitializer {

	private final JdbcTemplate jdbcTemplate;
	private final String datasourceUrl;

	public H2DevSchemaInitializer(JdbcTemplate jdbcTemplate, @Value("${spring.datasource.url}") String datasourceUrl) {
		this.jdbcTemplate = jdbcTemplate;
		this.datasourceUrl = datasourceUrl;
	}

	@PostConstruct
	void createSchemaForH2DevDatabase() {
		if (!datasourceUrl.startsWith("jdbc:h2:")) {
			return;
		}

		jdbcTemplate.execute("""
				create table if not exists users (
				    id uuid primary key,
				    email varchar(320) not null,
				    password_hash varchar(255) not null,
				    display_name varchar(120),
				    enabled boolean not null default false,
				    email_verified boolean not null default false,
				    account_status varchar(32) not null default 'UNVERIFIED',
				    created_at timestamp with time zone not null default current_timestamp,
				    updated_at timestamp with time zone not null default current_timestamp,
				    constraint users_account_status_check check (account_status in ('ACTIVE', 'DISABLED', 'LOCKED', 'UNVERIFIED')),
				    constraint users_email_key unique (email)
				)
				""");
		jdbcTemplate.execute("create index if not exists users_enabled_idx on users (enabled)");
		jdbcTemplate.execute("create index if not exists users_account_status_idx on users (account_status)");
	}
}
