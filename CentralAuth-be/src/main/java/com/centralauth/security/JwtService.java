package com.centralauth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.centralauth.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JwtService {

	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final ObjectMapper objectMapper;
	private final byte[] secret;
	private final String issuer;
	private final long expiresInSeconds;

	public JwtService(
			ObjectMapper objectMapper,
			@Value("${centralauth.jwt.secret}") String secret,
			@Value("${centralauth.jwt.issuer:central-auth}") String issuer,
			@Value("${centralauth.jwt.expires-in-seconds:3600}") long expiresInSeconds) {
		this.objectMapper = objectMapper;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.issuer = issuer;
		this.expiresInSeconds = expiresInSeconds;
	}

	public String createToken(User user) {
		Instant now = Instant.now();
		Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", user.id().toString());
		claims.put("email", user.email());
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(expiresInSeconds).getEpochSecond());

		String headerPart = encodeJson(header);
		String payloadPart = encodeJson(claims);
		String signaturePart = sign(headerPart + "." + payloadPart);
		return headerPart + "." + payloadPart + "." + signaturePart;
	}

	public Optional<JwtPrincipal> validate(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return Optional.empty();
		}

		String signedContent = parts[0] + "." + parts[1];
		if (!constantTimeEquals(sign(signedContent), parts[2])) {
			return Optional.empty();
		}

		try {
			Map<String, Object> claims = objectMapper.readValue(
					BASE64_URL_DECODER.decode(parts[1]),
					new TypeReference<>() {
					});
			if (!issuer.equals(claims.get("iss"))) {
				return Optional.empty();
			}
			if (Instant.now().getEpochSecond() >= numberClaim(claims, "exp")) {
				return Optional.empty();
			}
			String userId = (String) claims.get("sub");
			String email = (String) claims.get("email");
			return Optional.of(new JwtPrincipal(userId, email));
		}
		catch (RuntimeException | java.io.IOException ex) {
			return Optional.empty();
		}
	}

	private String encodeJson(Map<String, Object> value) {
		try {
			return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException("Unable to encode JWT", ex);
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to sign JWT", ex);
		}
	}

	private long numberClaim(Map<String, Object> claims, String name) {
		Object value = claims.get(name);
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new IllegalArgumentException("Missing numeric claim " + name);
	}

	private boolean constantTimeEquals(String expected, String actual) {
		byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
		byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
		if (expectedBytes.length != actualBytes.length) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < expectedBytes.length; i++) {
			result |= expectedBytes[i] ^ actualBytes[i];
		}
		return result == 0;
	}
}
