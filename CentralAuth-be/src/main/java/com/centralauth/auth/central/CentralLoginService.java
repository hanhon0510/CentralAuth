package com.centralauth.auth.central;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.centralauth.auth.AuthService;
import com.centralauth.auth.dto.AuthorizationCodeExchangeRequest;
import com.centralauth.auth.dto.CentralLoginContextResponse;
import com.centralauth.auth.dto.CentralLoginContinueRequest;
import com.centralauth.auth.dto.CentralLoginRedirectResponse;
import com.centralauth.auth.dto.CentralLoginRequest;
import com.centralauth.auth.dto.CentralLoginResponse;
import com.centralauth.auth.dto.CentralLoginTokenResponse;
import com.centralauth.auth.dto.SigninRequest;
import com.centralauth.auth.dto.UserResponse;
import com.centralauth.auth.central.AuthorizationCodeService.AuthorizationCodeContext;
import com.centralauth.auth.central.CentralLoginStateService.CentralLoginStateContext;
import com.centralauth.client.ClientApplication;
import com.centralauth.client.ClientApplicationService;

@Service
public class CentralLoginService {

	private final ClientApplicationService clientApplicationService;
	private final AuthService authService;
	private final AuthorizationCodeService authorizationCodeService;
	private final CentralLoginStateService centralLoginStateService;

	public CentralLoginService(
			ClientApplicationService clientApplicationService,
			AuthService authService,
			AuthorizationCodeService authorizationCodeService,
			CentralLoginStateService centralLoginStateService) {
		this.clientApplicationService = clientApplicationService;
		this.authService = authService;
		this.authorizationCodeService = authorizationCodeService;
		this.centralLoginStateService = centralLoginStateService;
	}

	@Transactional
	public CentralLoginContextResponse context(String clientId, String redirectUri, String state) {
		ClientApplication client = clientApplicationService.requireActiveClientForRedirect(clientId, redirectUri);
		String normalizedRedirectUri = redirectUri.trim();
		String normalizedState = normalizeState(state);
		String loginState = centralLoginStateService.issueState(client.clientId(), normalizedRedirectUri, normalizedState);
		return new CentralLoginContextResponse(
				client.clientId(),
				client.clientName(),
				normalizedRedirectUri,
				normalizedState,
				loginState);
	}

	@Transactional
	public CentralLoginResponse signin(CentralLoginRequest request, String clientIp) {
		CentralLoginStateContext context = requireStateBackedContext(
				request.loginState(),
				request.clientId(),
				request.redirectUri(),
				request.state());
		UserResponse user = authService.authenticateForCentralLogin(
				new SigninRequest(request.email(), request.password()),
				clientIp);
		centralLoginStateService.consumeState(request.loginState(), context);
		String code = authorizationCodeService.issueCode(
				user.id(),
				context.clientId(),
				context.redirectUri());
		return new CentralLoginResponse(
				context.redirectUri(),
				code,
				context.clientState(),
				redirectUrl(context.redirectUri(), code, context.clientState()));
	}

	@Transactional
	public CentralLoginRedirectResponse continueLogin(String userId, CentralLoginContinueRequest request) {
		CentralLoginStateContext context = requireStateBackedContext(
				request.loginState(),
				request.clientId(),
				request.redirectUri(),
				request.state());
		UserResponse user = authService.currentUser(userId);
		centralLoginStateService.consumeState(request.loginState(), context);
		String code = authorizationCodeService.issueCode(
				user.id(),
				context.clientId(),
				context.redirectUri());
		return new CentralLoginRedirectResponse(
				context.redirectUri(),
				code,
				context.clientState(),
				redirectUrl(context.redirectUri(), code, context.clientState()));
	}

	@Transactional
	public CentralLoginTokenResponse exchangeCode(AuthorizationCodeExchangeRequest request) {
		AuthorizationCodeContext context = authorizationCodeService.consumeCode(
				request.code(),
				request.clientId(),
				request.redirectUri());
		clientApplicationService.requireActiveClientForRedirect(context.clientId(), context.redirectUri());
		return authService.issueClientTokenForUser(context.userId(), context.clientId());
	}

	private CentralLoginStateContext requireStateBackedContext(
			String loginState,
			String clientId,
			String redirectUri,
			String state) {
		CentralLoginStateContext context = centralLoginStateService.requireValidState(
				loginState,
				clientId,
				redirectUri,
				normalizeState(state));
		clientApplicationService.requireActiveClientForRedirect(context.clientId(), context.redirectUri());
		return context;
	}

	private String redirectUrl(String redirectUri, String code, String state) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
				.queryParam("code", code);
		if (state != null) {
			builder.queryParam("state", state);
		}
		return builder.build().encode().toUriString();
	}

	private String normalizeState(String state) {
		if (state == null || state.isBlank()) {
			return null;
		}
		return state;
	}
}
