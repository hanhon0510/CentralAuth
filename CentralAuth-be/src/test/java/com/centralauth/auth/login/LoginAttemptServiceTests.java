package com.centralauth.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginAttemptServiceTests {

	@Test
	void requireLoginAllowedUsesLockDurationFallbackWhenLockExistsWithoutExpiry() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(redisTemplate.getExpire("login-lock:email:user@example.com", TimeUnit.SECONDS)).thenReturn(-1L);
		when(redisTemplate.getExpire("login-lock:ip:203.0.113.10", TimeUnit.SECONDS)).thenReturn(-2L);
		LoginAttemptService service = loginAttemptService(redisTemplate);

		assertThatThrownBy(() -> service.requireLoginAllowed("user@example.com", "203.0.113.10"))
				.isInstanceOfSatisfying(LoginTemporarilyLockedException.class, ex ->
						assertThat(ex.retryAfterSeconds()).isEqualTo(900));
	}

	@Test
	void requireLoginAllowedAllowsSigninWhenNoLockKeysExist() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(redisTemplate.getExpire("login-lock:email:user@example.com", TimeUnit.SECONDS)).thenReturn(-2L);
		when(redisTemplate.getExpire("login-lock:ip:203.0.113.10", TimeUnit.SECONDS)).thenReturn(-2L);
		LoginAttemptService service = loginAttemptService(redisTemplate);

		service.requireLoginAllowed("user@example.com", "203.0.113.10");
	}

	@Test
	void recordFailureStartsFailureWindowsAndLocksAtThreshold() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("login-failure:email:user@example.com")).thenReturn(5L);
		when(valueOperations.increment("login-failure:ip:203.0.113.10")).thenReturn(1L);
		LoginAttemptService service = loginAttemptService(redisTemplate);

		service.recordFailure("user@example.com", "203.0.113.10");

		verify(valueOperations).set("login-lock:email:user@example.com", "1", Duration.ofMinutes(15));
		verify(redisTemplate).expire("login-failure:ip:203.0.113.10", Duration.ofMinutes(15));
	}

	@Test
	void recordSuccessDeletesOnlyFailureCounters() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		LoginAttemptService service = loginAttemptService(redisTemplate);

		service.recordSuccess("user@example.com", "203.0.113.10");

		verify(redisTemplate).delete(List.of(
				"login-failure:email:user@example.com",
				"login-failure:ip:203.0.113.10"));
	}

	private LoginAttemptService loginAttemptService(StringRedisTemplate redisTemplate) {
		return new LoginAttemptService(
				redisTemplate,
				5,
				Duration.ofMinutes(15),
				Duration.ofMinutes(15),
				10,
				Duration.ofMinutes(1));
	}
}
