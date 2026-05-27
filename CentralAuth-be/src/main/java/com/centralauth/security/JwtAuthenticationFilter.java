package com.centralauth.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.centralauth.client.ClientApplicationService;
import com.centralauth.user.AccountStatus;
import com.centralauth.user.UserMapper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserMapper userMapper;
	private final AccessTokenRevocationService accessTokenRevocationService;
	private final ClientApplicationService clientApplicationService;

	public JwtAuthenticationFilter(
			JwtService jwtService,
			UserMapper userMapper,
			AccessTokenRevocationService accessTokenRevocationService,
			ClientApplicationService clientApplicationService) {
		this.jwtService = jwtService;
		this.userMapper = userMapper;
		this.accessTokenRevocationService = accessTokenRevocationService;
		this.clientApplicationService = clientApplicationService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authorization.substring(7);
		jwtService.validate(token)
				.filter(principal -> acceptsTokenForRequest(token, principal, request))
				.filter(this::hasActiveAccount)
				.ifPresentOrElse(
						principal -> SecurityContextHolder.getContext().setAuthentication(authentication(principal)),
						SecurityContextHolder::clearContext);

		filterChain.doFilter(request, response);
	}

	private boolean acceptsTokenForRequest(String token, JwtPrincipal principal, HttpServletRequest request) {
		if (accessTokenRevocationService.isRevoked(token, principal)) {
			return false;
		}
		return principal.centralAuthAccessToken() || clientAccessTokenCanReadCurrentUser(principal, request);
	}

	private boolean clientAccessTokenCanReadCurrentUser(JwtPrincipal principal, HttpServletRequest request) {
		return JwtPrincipal.CLIENT_ACCESS.equals(principal.tokenUse())
				&& HttpMethod.GET.matches(request.getMethod())
				&& "/api/v1/auth/me".equals(requestPath(request))
				&& clientApplicationService.activeClientExists(principal.audience());
	}

	private String requestPath(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
			return requestUri.substring(contextPath.length());
		}
		return requestUri;
	}

	private UsernamePasswordAuthenticationToken authentication(JwtPrincipal principal) {
		return new UsernamePasswordAuthenticationToken(
				principal.userId(),
				null,
				principal.roles().stream()
						.map(SimpleGrantedAuthority::new)
						.toList());
	}

	private boolean hasActiveAccount(JwtPrincipal principal) {
		return userMapper.findById(principal.userId())
				.filter(user -> user.accountStatus() == AccountStatus.ACTIVE)
				.isPresent();
	}
}
