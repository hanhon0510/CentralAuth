package com.centralauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.centralauth.auth.central.CentralLoginService;
import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.CentralLoginContextResponse;
import com.centralauth.auth.dto.CentralLoginContinueRequest;
import com.centralauth.auth.dto.CentralLoginRedirectResponse;
import com.centralauth.auth.dto.CentralLoginRequest;
import com.centralauth.auth.dto.CentralLoginResponse;
import com.centralauth.auth.dto.ForgotPasswordRequest;
import com.centralauth.auth.dto.LogoutRequest;
import com.centralauth.auth.dto.ResendVerificationOtpRequest;
import com.centralauth.auth.dto.ResendVerificationOtpResponse;
import com.centralauth.auth.dto.ResetPasswordRequest;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.dto.VerifyEmailRequest;
import com.centralauth.common.ApiResponse;
import com.centralauth.common.ClientIpResolver;
import com.centralauth.common.Messages;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final CentralLoginService centralLoginService;
	private final Messages messages;
	private final ClientIpResolver clientIpResolver;

	public AuthController(
			AuthService authService,
			CentralLoginService centralLoginService,
			Messages messages,
			ClientIpResolver clientIpResolver) {
		this.authService = authService;
		this.centralLoginService = centralLoginService;
		this.messages = messages;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/signup")
	public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(messages.get("auth.signup.success"), authService.signup(request));
	}

	@PostMapping("/signin")
	public ApiResponse<AuthResponse> signin(
			@Valid @RequestBody SigninRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(
				messages.get("auth.signin.success"),
				authService.signin(request, clientIpResolver.resolve(httpRequest)));
	}

	@GetMapping("/central-login/context")
	public ApiResponse<CentralLoginContextResponse> centralLoginContext(
			@RequestParam("client_id") String clientId,
			@RequestParam("redirect_uri") String redirectUri,
			@RequestParam(name = "state", required = false) String state) {
		return ApiResponse.success(
				messages.get("auth.centralLogin.context"),
				centralLoginService.context(clientId, redirectUri, state));
	}

	@PostMapping("/central-login")
	public ApiResponse<CentralLoginResponse> centralLogin(
			@Valid @RequestBody CentralLoginRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(
				messages.get("auth.centralLogin.success"),
				centralLoginService.signin(request, clientIpResolver.resolve(httpRequest)));
	}

	@PostMapping("/central-login/continue")
	public ApiResponse<CentralLoginRedirectResponse> continueCentralLogin(
			Authentication authentication,
			@Valid @RequestBody CentralLoginContinueRequest request) {
		return ApiResponse.success(
				messages.get("auth.centralLogin.success"),
				centralLoginService.continueLogin((String) authentication.getPrincipal(), request));
	}

	@PostMapping("/verify-email")
	public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		authService.verifyEmail(request);
		return ApiResponse.success(messages.get("auth.email.verified"), null);
	}

	@PostMapping("/resend-verification-otp")
	public ApiResponse<ResendVerificationOtpResponse> resendVerificationOtp(
			@Valid @RequestBody ResendVerificationOtpRequest request) {
		return ApiResponse.success(messages.get("auth.verificationOtp.resent"), authService.resendVerificationOtp(request));
	}

	@PostMapping("/forgot-password")
	public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.forgotPassword(request);
		return ApiResponse.success(messages.get("auth.passwordReset.requested"), null);
	}

	@PostMapping("/reset-password")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ApiResponse.success(messages.get("auth.passwordReset.success"), null);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(Authentication authentication, @Valid @RequestBody LogoutRequest request) {
		authService.logout((String) authentication.getPrincipal(), request);
		return ApiResponse.success(messages.get("auth.logout.success"), null);
	}

	@PostMapping("/logout-all-devices")
	public ApiResponse<Void> logoutAllDevices(Authentication authentication) {
		authService.logoutAllDevices((String) authentication.getPrincipal());
		return ApiResponse.success(messages.get("auth.logoutAllDevices.success"), null);
	}

	@GetMapping("/me")
	public ApiResponse<UserResponse> me(Authentication authentication) {
		return ApiResponse.success(messages.get("auth.currentUser"), authService.currentUser((String) authentication.getPrincipal()));
	}
}
