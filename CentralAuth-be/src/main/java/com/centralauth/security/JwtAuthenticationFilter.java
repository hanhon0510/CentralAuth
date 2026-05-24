package com.centralauth.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.centralauth.user.AccountStatus;
import com.centralauth.user.UserMapper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserMapper userMapper;

	public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper) {
		this.jwtService = jwtService;
		this.userMapper = userMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		jwtService.validate(authorization.substring(7))
				.filter(JwtPrincipal::centralAuthAccessToken)
				.filter(this::hasActiveAccount)
				.ifPresentOrElse(
						principal -> SecurityContextHolder.getContext().setAuthentication(authentication(principal)),
						SecurityContextHolder::clearContext);

		filterChain.doFilter(request, response);
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
