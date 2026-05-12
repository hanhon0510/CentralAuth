package com.centralauth.auth;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.ResendVerificationOtpRequest;
import com.centralauth.auth.dto.ResendVerificationOtpResponse;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.SignupRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.dto.VerifyEmailRequest;
import com.centralauth.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success("Signup successful", authService.signup(request));
	}

	@PostMapping("/signin")
	public ApiResponse<AuthResponse> signin(@Valid @RequestBody SigninRequest request) {
		return ApiResponse.success("Signin successful", authService.signin(request));
	}

	@PostMapping("/verify-email")
	public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		authService.verifyEmail(request);
		return ApiResponse.success("Email verified", null);
	}

	@PostMapping("/resend-verification-otp")
	public ApiResponse<ResendVerificationOtpResponse> resendVerificationOtp(
			@Valid @RequestBody ResendVerificationOtpRequest request) {
		return ApiResponse.success("Verification OTP resent", authService.resendVerificationOtp(request));
	}

	@GetMapping("/me")
	public ApiResponse<UserResponse> me(Authentication authentication) {
		return ApiResponse.success("Current user", authService.currentUser((String) authentication.getPrincipal()));
	}
}
