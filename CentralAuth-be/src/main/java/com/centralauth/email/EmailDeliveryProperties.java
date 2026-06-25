package com.centralauth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "centralauth.email")
public record EmailDeliveryProperties(
		String mode,
		String appName,
		String from,
		String frontendBaseUrl) {
}
