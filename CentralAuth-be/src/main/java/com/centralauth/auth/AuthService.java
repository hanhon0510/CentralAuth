package com.centralauth.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.LogoutRequest;
import com.centralauth.auth.dto.ResendVerificationOtpRequest;
import com.centralauth.auth.dto.ResendVerificationOtpResponse;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.dto.VerifyEmailRequest;
import com.centralauth.security.JwtService;
import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

@Service
public class AuthService {

	private static final String DEFAULT_ROLE = "ROLE_USER";

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EmailVerificationService emailVerificationService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			EmailVerificationService emailVerificationService,
			RefreshTokenService refreshTokenService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.emailVerificationService = emailVerificationService;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional
	public AuthResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		User user = new User(
				UUID.randomUUID().toString(),
				email,
				passwordEncoder.encode(request.password()),
				normalizeDisplayName(request.displayName()),
				false,
				false,
				null,
				null);
		try {
			userMapper.insert(user);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateEmailException();
		}
		userMapper.insertRole(user.id(), DEFAULT_ROLE);
		emailVerificationService.issueOtp(email);

		User savedUser = userMapper.findByEmail(email).orElse(user);
		return toAuthResponse(savedUser);
	}

	public void verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		emailVerificationService.requireValidOtp(email, request.otp());
		if (userMapper.verifyEmail(email) == 0) {
			throw new InvalidEmailVerificationOtpException();
		}
		emailVerificationService.consumeOtp(email);
	}

	public ResendVerificationOtpResponse resendVerificationOtp(ResendVerificationOtpRequest request) {
		String email = normalizeEmail(request.email());
		User user = userMapper.findByEmail(email).orElseThrow(EmailVerificationNotPendingException::new);
		if (user.emailVerified()) {
			throw new EmailVerificationNotPendingException();
		}
		int resendCooldownSeconds = emailVerificationService.resendOtp(email);
		return new ResendVerificationOtpResponse(resendCooldownSeconds);
	}

	@Transactional
	public AuthResponse signin(SigninRequest request) {
		User user = userMapper.findByEmail(normalizeEmail(request.email()))
				.filter(User::enabled)
				.filter(User::emailVerified)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElseThrow(InvalidCredentialsException::new);

		return toAuthResponse(user);
	}

	public void logout(String userId, LogoutRequest request) {
		refreshTokenService.revokeRefreshToken(userId, request.refreshToken());
	}

	public void logoutAllDevices(String userId) {
		refreshTokenService.revokeAllActiveRefreshTokens(userId);
	}

	public UserResponse currentUser(String userId) {
		return userMapper.findById(userId)
				.filter(User::enabled)
				.map(UserResponse::from)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private AuthResponse toAuthResponse(User user) {
		return new AuthResponse(
				jwtService.createToken(user),
				refreshTokenService.issueRefreshToken(user.id()),
				UserResponse.from(user));
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
