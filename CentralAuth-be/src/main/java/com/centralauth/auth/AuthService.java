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
import com.centralauth.auth.dto.CentralLoginTokenResponse;
import com.centralauth.auth.dto.ForgotPasswordRequest;
import com.centralauth.auth.dto.LogoutResponse;
import com.centralauth.auth.dto.LogoutRequest;
import com.centralauth.auth.dto.ResendVerificationOtpRequest;
import com.centralauth.auth.dto.ResendVerificationOtpResponse;
import com.centralauth.auth.dto.RefreshTokenRequest;
import com.centralauth.auth.dto.ResetPasswordRequest;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.dto.VerifyEmailRequest;
import com.centralauth.auth.exception.DuplicateEmailException;
import com.centralauth.auth.exception.EmailVerificationNotPendingException;
import com.centralauth.auth.exception.EmailVerificationOtpResendThrottledException;
import com.centralauth.auth.exception.InvalidCredentialsException;
import com.centralauth.auth.exception.InvalidEmailVerificationOtpException;
import com.centralauth.auth.exception.InvalidRefreshTokenException;
import com.centralauth.auth.login.LoginAttemptService;
import com.centralauth.auth.login.LoginRateLimitExceededException;
import com.centralauth.auth.login.LoginTemporarilyLockedException;
import com.centralauth.auth.logging.StructuredAuthLogger;
import com.centralauth.auth.password.PasswordResetService;
import com.centralauth.auth.token.RefreshToken;
import com.centralauth.auth.token.RefreshTokenService;
import com.centralauth.auth.verification.EmailVerificationService;
import com.centralauth.client.ClientApplicationService;
import com.centralauth.event.auth.LoginFailedEvent;
import com.centralauth.event.auth.LoginSucceededEvent;
import com.centralauth.event.auth.UserRegisteredEvent;
import com.centralauth.event.auth.UserLoggedOutEvent;
import com.centralauth.event.auth.UserVerifiedEvent;
import com.centralauth.security.AccessTokenRevocationService;
import com.centralauth.security.JwtPrincipal;
import com.centralauth.security.JwtService;
import com.centralauth.user.AccountStatus;
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
	private final AccessTokenRevocationService accessTokenRevocationService;
	private final ClientApplicationService clientApplicationService;

	public AuthService(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			EmailVerificationService emailVerificationService,
			RefreshTokenService refreshTokenService,
			LoginAttemptService loginAttemptService,
			PasswordResetService passwordResetService,
			ApplicationEventPublisher eventPublisher,
			AccessTokenRevocationService accessTokenRevocationService,
			ClientApplicationService clientApplicationService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.emailVerificationService = emailVerificationService;
		this.refreshTokenService = refreshTokenService;
		this.loginAttemptService = loginAttemptService;
		this.passwordResetService = passwordResetService;
		this.eventPublisher = eventPublisher;
		this.accessTokenRevocationService = accessTokenRevocationService;
		this.clientApplicationService = clientApplicationService;
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
				AccountStatus.UNVERIFIED,
				null,
				null);
		try {
			userMapper.insert(user);
		}
		catch (DuplicateKeyException ex) {
			StructuredAuthLogger.signupFailed(email, "DUPLICATE_EMAIL");
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
		StructuredAuthLogger.userRegistered(savedUser.id(), savedUser.email());
		return response;
	}

	@Transactional
	public void verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		try {
			emailVerificationService.requireValidOtp(email, request.otp());
		}
		catch (InvalidEmailVerificationOtpException ex) {
			StructuredAuthLogger.emailVerificationFailed(email, "INVALID_OTP");
			throw ex;
		}
		if (userMapper.verifyEmail(email) == 0) {
			StructuredAuthLogger.emailVerificationFailed(email, "NOT_PENDING");
			throw new InvalidEmailVerificationOtpException();
		}
		emailVerificationService.consumeOtp(email);
		User verifiedUser = userMapper.findByEmail(email).orElseThrow(InvalidEmailVerificationOtpException::new);
		eventPublisher.publishEvent(new UserVerifiedEvent(
				verifiedUser.id(),
				verifiedUser.email(),
				Instant.now()));
		StructuredAuthLogger.emailVerified(verifiedUser.id(), verifiedUser.email());
	}

	public ResendVerificationOtpResponse resendVerificationOtp(ResendVerificationOtpRequest request) {
		String email = normalizeEmail(request.email());
		User user = userMapper.findByEmail(email).orElse(null);
		if (user == null) {
			StructuredAuthLogger.emailVerificationFailed(email, "NOT_PENDING");
			throw new EmailVerificationNotPendingException();
		}
		if (user.emailVerified()) {
			StructuredAuthLogger.emailVerificationFailed(email, "NOT_PENDING");
			throw new EmailVerificationNotPendingException();
		}
		int resendCooldownSeconds;
		try {
			resendCooldownSeconds = emailVerificationService.resendOtp(email);
		}
		catch (EmailVerificationOtpResendThrottledException ex) {
			StructuredAuthLogger.emailVerificationFailed(email, "OTP_RESEND_THROTTLED");
			throw ex;
		}
		StructuredAuthLogger.emailVerificationOtpResent(user.id(), user.email());
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
		return toAuthResponse(authenticateUser(request, clientIp));
	}

	@Transactional
	public AuthResponse refresh(RefreshTokenRequest request) {
		RefreshToken refreshToken = refreshTokenService.requireActiveRefreshToken(request.refreshToken());
		User user = userMapper.findById(refreshToken.userId())
				.filter(this::activeAccount)
				.orElseThrow(InvalidRefreshTokenException::new);
		String rotatedRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);
		return new AuthResponse(
				jwtService.createToken(user, rolesFor(user.id())),
				rotatedRefreshToken,
				UserResponse.from(user));
	}

	@Transactional
	public CentralLoginTokenResponse issueClientTokenForUser(String userId, String clientId) {
		User user = userMapper.findById(userId)
				.filter(this::activeAccount)
				.orElseThrow(InvalidCredentialsException::new);
		CentralLoginTokenResponse response = new CentralLoginTokenResponse(
				jwtService.createClientToken(user, clientId),
				UserResponse.from(user));
		StructuredAuthLogger.clientTokenIssued(user.id(), clientId);
		return response;
	}

	private User authenticateUser(SigninRequest request, String clientIp) {
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
				.filter(this::activeAccount)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElse(null);
		if (user == null) {
			loginAttemptService.recordFailure(email, clientIp);
			publishLoginFailed(email, clientIp, "INVALID_CREDENTIALS");
			throw new InvalidCredentialsException();
		}

		loginAttemptService.recordSuccess(email, clientIp);
		eventPublisher.publishEvent(new LoginSucceededEvent(
				user.id(),
				user.email(),
				clientIp,
				Instant.now()));
		StructuredAuthLogger.loginSucceeded(user.id(), user.email(), clientIp);
		return user;
	}

	@Transactional
	public LogoutResponse logout(String userId, String accessToken, LogoutRequest request) {
		refreshTokenService.revokeRefreshToken(userId, request.refreshToken());
		revokeCurrentCentralAccessToken(userId, accessToken);
		eventPublisher.publishEvent(new UserLoggedOutEvent(userId, false, Instant.now()));
		StructuredAuthLogger.loggedOut(userId, false);
		return logoutResponse();
	}

	@Transactional
	public LogoutResponse logoutAllDevices(String userId, String accessToken) {
		refreshTokenService.revokeAllActiveRefreshTokens(userId);
		revokeCurrentCentralAccessToken(userId, accessToken);
		accessTokenRevocationService.revokeTokensIssuedAtOrBefore(userId, Instant.now());
		eventPublisher.publishEvent(new UserLoggedOutEvent(userId, true, Instant.now()));
		StructuredAuthLogger.loggedOut(userId, true);
		return logoutResponse();
	}

	public UserResponse currentUser(String userId) {
		return userMapper.findById(userId)
				.filter(this::activeAccount)
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
		StructuredAuthLogger.loginFailed(email, clientIp, reason);
	}

	private void revokeCurrentCentralAccessToken(String userId, String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return;
		}
		jwtService.validate(accessToken)
				.filter(JwtPrincipal::centralAuthAccessToken)
				.filter(principal -> userId.equals(principal.userId()))
				.ifPresent(principal -> accessTokenRevocationService.revokeToken(
						accessToken,
						principal.expiresAtEpochSecond()));
	}

	private LogoutResponse logoutResponse() {
		return new LogoutResponse(clientApplicationService.activeClientLogoutUris());
	}

	private boolean activeAccount(User user) {
		return user.accountStatus() == AccountStatus.ACTIVE;
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
