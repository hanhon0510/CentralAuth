package com.centralauth.auth;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.ForgotPasswordRequest;
import com.centralauth.auth.dto.LogoutRequest;
import com.centralauth.auth.dto.ResendVerificationOtpRequest;
import com.centralauth.auth.dto.ResendVerificationOtpResponse;
import com.centralauth.auth.dto.ResetPasswordRequest;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.dto.VerifyEmailRequest;
import com.centralauth.auth.exception.DuplicateEmailException;
import com.centralauth.auth.exception.EmailVerificationNotPendingException;
import com.centralauth.auth.exception.InvalidCredentialsException;
import com.centralauth.auth.exception.InvalidEmailVerificationOtpException;
import com.centralauth.auth.login.LoginAttemptService;
import com.centralauth.auth.login.LoginRateLimitExceededException;
import com.centralauth.auth.login.LoginTemporarilyLockedException;
import com.centralauth.auth.password.PasswordResetService;
import com.centralauth.auth.token.RefreshTokenService;
import com.centralauth.auth.verification.EmailVerificationService;
import com.centralauth.event.auth.LoginFailedEvent;
import com.centralauth.event.auth.LoginSucceededEvent;
import com.centralauth.event.auth.UserRegisteredEvent;
import com.centralauth.event.auth.UserLoggedOutEvent;
import com.centralauth.event.auth.UserVerifiedEvent;
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
	private final LoginAttemptService loginAttemptService;
	private final PasswordResetService passwordResetService;
	private final ApplicationEventPublisher eventPublisher;

	public AuthService(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			EmailVerificationService emailVerificationService,
			RefreshTokenService refreshTokenService,
			LoginAttemptService loginAttemptService,
			PasswordResetService passwordResetService,
			ApplicationEventPublisher eventPublisher) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.emailVerificationService = emailVerificationService;
		this.refreshTokenService = refreshTokenService;
		this.loginAttemptService = loginAttemptService;
		this.passwordResetService = passwordResetService;
		this.eventPublisher = eventPublisher;
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
		AuthResponse response = toAuthResponse(savedUser);
		eventPublisher.publishEvent(new UserRegisteredEvent(
				savedUser.id(),
				savedUser.email(),
				savedUser.displayName(),
				Instant.now()));
		return response;
	}

	@Transactional
	public void verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		emailVerificationService.requireValidOtp(email, request.otp());
		if (userMapper.verifyEmail(email) == 0) {
			throw new InvalidEmailVerificationOtpException();
		}
		emailVerificationService.consumeOtp(email);
		User verifiedUser = userMapper.findByEmail(email).orElseThrow(InvalidEmailVerificationOtpException::new);
		eventPublisher.publishEvent(new UserVerifiedEvent(
				verifiedUser.id(),
				verifiedUser.email(),
				Instant.now()));
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

	public void forgotPassword(ForgotPasswordRequest request) {
		passwordResetService.requestReset(request.email());
	}

	public void resetPassword(ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.token(), request.newPassword());
	}

	@Transactional
	public AuthResponse signin(SigninRequest request, String clientIp) {
		String email = normalizeEmail(request.email());
		try {
			loginAttemptService.recordAttempt(email, clientIp);
			loginAttemptService.requireLoginAllowed(email, clientIp);
		}
		catch (LoginRateLimitExceededException ex) {
			publishLoginFailed(email, clientIp, "RATE_LIMITED");
			throw ex;
		}
		catch (LoginTemporarilyLockedException ex) {
			publishLoginFailed(email, clientIp, "TEMPORARILY_LOCKED");
			throw ex;
		}

		User user = userMapper.findByEmail(email)
				.filter(User::enabled)
				.filter(User::emailVerified)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElse(null);
		if (user == null) {
			loginAttemptService.recordFailure(email, clientIp);
			publishLoginFailed(email, clientIp, "INVALID_CREDENTIALS");
			throw new InvalidCredentialsException();
		}

		loginAttemptService.recordSuccess(email, clientIp);
		AuthResponse response = toAuthResponse(user);
		eventPublisher.publishEvent(new LoginSucceededEvent(
				user.id(),
				user.email(),
				clientIp,
				Instant.now()));
		return response;
	}

	@Transactional
	public void logout(String userId, LogoutRequest request) {
		refreshTokenService.revokeRefreshToken(userId, request.refreshToken());
		eventPublisher.publishEvent(new UserLoggedOutEvent(userId, false, Instant.now()));
	}

	@Transactional
	public void logoutAllDevices(String userId) {
		refreshTokenService.revokeAllActiveRefreshTokens(userId);
		eventPublisher.publishEvent(new UserLoggedOutEvent(userId, true, Instant.now()));
	}

	public UserResponse currentUser(String userId) {
		return userMapper.findById(userId)
				.filter(User::enabled)
				.map(UserResponse::from)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private AuthResponse toAuthResponse(User user) {
		return new AuthResponse(
				jwtService.createToken(user, rolesFor(user.id())),
				refreshTokenService.issueRefreshToken(user.id()),
				UserResponse.from(user));
	}

	private List<String> rolesFor(String userId) {
		List<String> roles = userMapper.findRolesByUserId(userId);
		if (roles.isEmpty()) {
			return List.of(DEFAULT_ROLE);
		}
		return roles;
	}

	private void publishLoginFailed(String email, String clientIp, String reason) {
		eventPublisher.publishEvent(new LoginFailedEvent(email, clientIp, reason, Instant.now()));
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
