package com.centralauth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/v1/health",
								"/api/v1/auth/signup",
								"/api/v1/auth/signin",
								"/api/v1/auth/verify-email",
								"/api/v1/auth/resend-verification-otp",
								"/api/v1/auth/forgot-password",
								"/api/v1/auth/reset-password",
								"/actuator/health",
								"/actuator/info").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
