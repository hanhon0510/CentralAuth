package com.centralauth.auth.central;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.centralauth.auth.exception.InvalidCentralLoginStateException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CentralLoginStateService {

	private static final Duration STATE_TTL = Duration.ofMinutes(10);
	private static final int STATE_BYTES = 32;
	private static final String STATE_KEY_PREFIX = "auth_state:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final SecureRandom secureRandom = new SecureRandom();

	public CentralLoginStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public String issueState(String clientId, String redirectUri, String clientState) {
		String loginState = generateState();
		redisTemplate.opsForValue().set(
				STATE_KEY_PREFIX + loginState,
				serialize(new CentralLoginStateContext(clientId, redirectUri, clientState)),
				STATE_TTL);
		return loginState;
	}

	public CentralLoginStateContext requireValidState(
			String loginState,
			String clientId,
			String redirectUri,
			String clientState) {
		String normalizedLoginState = requirePresent(loginState);
		String serialized = redisTemplate.opsForValue().get(STATE_KEY_PREFIX + normalizedLoginState);
		if (serialized == null || serialized.isBlank()) {
			throw new InvalidCentralLoginStateException();
		}
		CentralLoginStateContext context = deserialize(serialized);
		requireMatches(context, clientId, redirectUri, clientState);
		return context;
	}

	public void consumeState(String loginState, CentralLoginStateContext expectedContext) {
		String normalizedLoginState = requirePresent(loginState);
		String serialized = redisTemplate.opsForValue().getAndDelete(STATE_KEY_PREFIX + normalizedLoginState);
		if (serialized == null || serialized.isBlank()) {
			throw new InvalidCentralLoginStateException();
		}
		CentralLoginStateContext consumedContext = deserialize(serialized);
		requireMatches(
				consumedContext,
				expectedContext.clientId(),
				expectedContext.redirectUri(),
				expectedContext.clientState());
	}

	private String generateState() {
		byte[] randomBytes = new byte[STATE_BYTES];
		secureRandom.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}

	private String serialize(CentralLoginStateContext context) {
		try {
			return objectMapper.writeValueAsString(context);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize central login state", ex);
		}
	}

	private CentralLoginStateContext deserialize(String value) {
		try {
			return objectMapper.readValue(value, CentralLoginStateContext.class);
		}
		catch (JsonProcessingException ex) {
			throw new InvalidCentralLoginStateException();
		}
	}

	private void requireMatches(
			CentralLoginStateContext context,
			String clientId,
			String redirectUri,
			String clientState) {
		if (!Objects.equals(context.clientId(), requirePresent(clientId))
				|| !Objects.equals(context.redirectUri(), requirePresent(redirectUri))
				|| !Objects.equals(context.clientState(), normalizeClientState(clientState))) {
			throw new InvalidCentralLoginStateException();
		}
	}

	private String requirePresent(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidCentralLoginStateException();
		}
		return value.trim();
	}

	private String normalizeClientState(String state) {
		if (state == null || state.isBlank()) {
			return null;
		}
		return state;
	}

	public record CentralLoginStateContext(
			String clientId,
			String redirectUri,
			String clientState) {
	}
}
