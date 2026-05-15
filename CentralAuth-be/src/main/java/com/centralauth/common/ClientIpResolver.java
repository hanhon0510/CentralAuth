package com.centralauth.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

	public String resolve(HttpServletRequest request) {
		String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
		if (forwardedFor != null) {
			return forwardedFor;
		}
		String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
		if (realIp != null) {
			return realIp;
		}
		String remoteAddr = request.getRemoteAddr();
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return "unknown";
		}
		return remoteAddr.trim();
	}

	private String firstHeaderValue(String headerValue) {
		if (headerValue == null || headerValue.isBlank()) {
			return null;
		}
		for (String value : headerValue.split(",")) {
			String trimmed = value.trim();
			if (!trimmed.isBlank()) {
				return trimmed;
			}
		}
		return null;
	}
}
