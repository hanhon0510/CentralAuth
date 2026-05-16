package com.centralauth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.centralauth.user.AccountStatus;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

class JwtAuthenticationFilterTests {

	private final JwtService jwtService = new JwtService(
			JsonMapper.builder().findAndAddModules().build(),
			"test-secret-with-at-least-32-characters",
			"central-auth-test",
			3600);
	private final UserMapper userMapper = mock(UserMapper.class);
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userMapper);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatedRequestUsesJwtRoleClaimsAsSpringAuthorities() throws ServletException, IOException {
		User user = new User(
				"4923a502-e1f7-4631-bf23-b93a347ccca6",
				"admin-filter@example.com",
				"password-hash",
				"Admin Filter",
				true,
				true,
				AccountStatus.ACTIVE,
				null,
				null);
		when(userMapper.findById(user.id())).thenReturn(Optional.of(user));
		String token = jwtService.createToken(user, List.of("ROLE_USER", "ROLE_ADMIN"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		CapturingFilterChain filterChain = new CapturingFilterChain();

		filter.doFilter(request, response, filterChain);

		assertThat(filterChain.authentication).isNotNull();
		assertThat(filterChain.authentication.getPrincipal()).isEqualTo(user.id());
		assertThat(filterChain.authentication.getAuthorities())
				.extracting(Object::toString)
				.containsExactly("ROLE_USER", "ROLE_ADMIN");
	}

	private static final class CapturingFilterChain implements FilterChain {

		private Authentication authentication;

		@Override
		public void doFilter(ServletRequest request, ServletResponse response) {
			authentication = SecurityContextHolder.getContext().getAuthentication();
		}
	}
}
