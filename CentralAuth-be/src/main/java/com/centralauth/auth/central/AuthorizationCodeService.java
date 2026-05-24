package com.centralauth.auth.central;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.centralauth.auth.exception.InvalidAuthorizationCodeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuthorizationCodeService {

	private static final Duration CODE_TTL = Duration.ofMinutes(5);
	private static final int CODE_BYTES = 32;
	private static final String CODE_KEY_PREFIX = "auth_code:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthorizationCodeService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public String issueCode(String userId, String clientId, String redirectUri) {
		String code = generateCode();
		redisTemplate.opsForValue().set(
				CODE_KEY_PREFIX + code,
				serialize(new AuthorizationCodeContext(userId, clientId, redirectUri)),
				CODE_TTL);
		return code;
	}

	public AuthorizationCodeContext consumeCode(String code, String clientId, String redirectUri) {
		String normalizedCode = requirePresent(code);
		String serialized = redisTemplate.opsForValue().getAndDelete(CODE_KEY_PREFIX + normalizedCode);
		if (serialized == null || serialized.isBlank()) {
			throw new InvalidAuthorizationCodeException();
		}

		AuthorizationCodeContext context = deserialize(serialized);
		if (!Objects.equals(context.clientId(), requirePresent(clientId))
				|| !Objects.equals(context.redirectUri(), requirePresent(redirectUri))) {
			throw new InvalidAuthorizationCodeException();
		}
		return context;
	}

	private String generateCode() {
		byte[] randomBytes = new byte[CODE_BYTES];
		secureRandom.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}

	private String serialize(AuthorizationCodeContext context) {
		try {
			return objectMapper.writeValueAsString(context);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize authorization code context", ex);
		}
	}

	private AuthorizationCodeContext deserialize(String value) {
		try {
			return objectMapper.readValue(value, AuthorizationCodeContext.class);
		}
		catch (JsonProcessingException ex) {
			throw new InvalidAuthorizationCodeException();
		}
	}

	private String requirePresent(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidAuthorizationCodeException();
		}
		return value.trim();
	}

	public record AuthorizationCodeContext(
			String userId,
			String clientId,
			String redirectUri) {
	}
}
