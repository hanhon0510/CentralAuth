package com.centralauth.auth.central;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centralauth.auth.AuthService;
import com.centralauth.auth.dto.AuthResponse;
import com.centralauth.auth.dto.CentralLoginContextResponse;
import com.centralauth.auth.dto.CentralLoginContinueRequest;
import com.centralauth.auth.dto.CentralLoginRedirectResponse;
import com.centralauth.auth.dto.CentralLoginRequest;
import com.centralauth.auth.dto.CentralLoginResponse;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.client.ClientApplication;
import com.centralauth.client.ClientApplicationService;

@Service
public class CentralLoginService {

	private final ClientApplicationService clientApplicationService;
	private final AuthService authService;
	private final AuthorizationCodeService authorizationCodeService;

	public CentralLoginService(
			ClientApplicationService clientApplicationService,
			AuthService authService,
			AuthorizationCodeService authorizationCodeService) {
		this.clientApplicationService = clientApplicationService;
		this.authService = authService;
		this.authorizationCodeService = authorizationCodeService;
	}

	@Transactional(readOnly = true)
	public CentralLoginContextResponse context(String clientId, String redirectUri, String state) {
		ClientApplication client = clientApplicationService.requireActiveClientForRedirect(clientId, redirectUri);
		return new CentralLoginContextResponse(
				client.clientId(),
				client.clientName(),
				redirectUri.trim(),
				normalizeState(state));
	}

	@Transactional
	public CentralLoginResponse signin(CentralLoginRequest request, String clientIp) {
		CentralLoginContextResponse context = context(request.clientId(), request.redirectUri(), request.state());
		AuthResponse auth = authService.signin(new SigninRequest(request.email(), request.password()), clientIp);
		String code = authorizationCodeService.issueCode(
				auth.user().id(),
				context.clientId(),
				context.redirectUri(),
				context.state());
		return new CentralLoginResponse(context.redirectUri(), code, context.state(), auth);
	}

	@Transactional
	public CentralLoginRedirectResponse continueLogin(String userId, CentralLoginContinueRequest request) {
		CentralLoginContextResponse context = context(request.clientId(), request.redirectUri(), request.state());
		UserResponse user = authService.currentUser(userId);
		String code = authorizationCodeService.issueCode(
				user.id(),
				context.clientId(),
				context.redirectUri(),
				context.state());
		return new CentralLoginRedirectResponse(context.redirectUri(), code, context.state());
	}

	private String normalizeState(String state) {
		if (state == null || state.isBlank()) {
			return null;
		}
		return state;
	}
}
