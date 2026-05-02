package com.centralauth.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.security.JwtService;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

@Service
public class AuthService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		User user = new User(
				UUID.randomUUID().toString(),
				email,
				passwordEncoder.encode(request.password()),
				normalizeDisplayName(request.displayName()),
				true,
				false,
				null,
				null);
		try {
			userMapper.insert(user);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateEmailException();
		}

		User savedUser = userMapper.findByEmail(email).orElse(user);
		return toAuthResponse(savedUser);
	}

	public AuthResponse signin(SigninRequest request) {
		User user = userMapper.findByEmail(normalizeEmail(request.email()))
				.filter(User::enabled)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElseThrow(InvalidCredentialsException::new);

		return toAuthResponse(user);
	}

	public UserResponse currentUser(String userId) {
		return userMapper.findById(userId)
				.filter(User::enabled)
				.map(UserResponse::from)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private AuthResponse toAuthResponse(User user) {
		return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return null;
		}
		return displayName.trim();
	}
}
