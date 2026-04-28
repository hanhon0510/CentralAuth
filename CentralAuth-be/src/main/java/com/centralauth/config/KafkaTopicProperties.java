package com.centralauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "centralauth.kafka.topics")
public record KafkaTopicProperties(
		String userRegistered,
		String userVerified,
		String loginSucceeded,
		String loginFailed,
		String logout,
		String passwordResetRequested,
		String passwordChanged
) {
}
