package com.centralauth.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientApplicationService {

	private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,120}");
	private static final int MAX_CLIENT_NAME_LENGTH = 255;
	private static final int MAX_REDIRECT_URI_LENGTH = 2048;
	private static final int MAX_ORIGIN_LENGTH = 512;
	private static final int MAX_LOGOUT_URI_LENGTH = 2048;

	private final ClientApplicationMapper clientApplicationMapper;

	public ClientApplicationService(ClientApplicationMapper clientApplicationMapper) {
		this.clientApplicationMapper = clientApplicationMapper;
	}

	@Transactional
	public ClientApplication createClient(
			String clientId,
			String clientName,
			List<String> redirectUris,
			List<String> allowedOrigins,
			List<String> logoutUris,
			boolean active) {
		ClientMetadata metadata = validateMetadata(clientId, clientName, redirectUris, allowedOrigins, logoutUris);
		if (clientApplicationMapper.findByClientId(metadata.clientId()).isPresent()) {
			throw new DuplicateClientApplicationException();
		}

		clientApplicationMapper.insert(new ClientApplication(
				metadata.clientId(),
				metadata.clientName(),
				active,
				null,
				null,
				metadata.redirectUris(),
				metadata.allowedOrigins(),
				metadata.logoutUris()));
		replaceRedirectUris(metadata.clientId(), metadata.redirectUris());
		replaceAllowedOrigins(metadata.clientId(), metadata.allowedOrigins());
		replaceLogoutUris(metadata.clientId(), metadata.logoutUris());
		return findExisting(metadata.clientId());
	}

	@Transactional
	public ClientApplication updateClient(
			String clientId,
			String clientName,
			List<String> redirectUris,
			List<String> allowedOrigins,
			List<String> logoutUris,
			boolean active) {
		ClientMetadata metadata = validateMetadata(clientId, clientName, redirectUris, allowedOrigins, logoutUris);
		ensureExists(metadata.clientId());
		int updated = clientApplicationMapper.updateClient(metadata.clientId(), metadata.clientName(), active);
		if (updated == 0) {
			throw new ClientApplicationNotFoundException();
		}
		replaceRedirectUris(metadata.clientId(), metadata.redirectUris());
		replaceAllowedOrigins(metadata.clientId(), metadata.allowedOrigins());
		replaceLogoutUris(metadata.clientId(), metadata.logoutUris());
		return findExisting(metadata.clientId());
	}

	@Transactional(readOnly = true)
	public List<ClientApplication> listClients() {
		return clientApplicationMapper.findAll();
	}

	@Transactional
	public ClientApplication updateActive(String clientId, boolean active) {
		String normalizedClientId = validateClientId(clientId);
		int updated = clientApplicationMapper.updateActive(normalizedClientId, active);
		if (updated == 0) {
			throw new ClientApplicationNotFoundException();
		}
		return findExisting(normalizedClientId);
	}

	@Transactional(readOnly = true)
	public ClientApplication requireActiveClientForRedirect(String clientId, String redirectUri) {
		String normalizedClientId = validateClientId(clientId);
		String normalizedRedirectUri = validateRedirectUri(redirectUri);
		ClientApplication client = findExisting(normalizedClientId);
		if (!client.active()) {
			throw new InactiveClientApplicationException();
		}
		if (!client.redirectUris().contains(normalizedRedirectUri)) {
			throw new InvalidClientMetadataException();
		}
		return client;
	}

	@Transactional(readOnly = true)
	public boolean activeClientExists(String clientId) {
		try {
			return clientApplicationMapper.findByClientId(validateClientId(clientId))
					.filter(ClientApplication::active)
					.isPresent();
		}
		catch (InvalidClientMetadataException ex) {
			return false;
		}
	}

	@Transactional(readOnly = true)
	public List<String> activeClientLogoutUris() {
		return listClients().stream()
				.filter(ClientApplication::active)
				.flatMap(client -> client.logoutUris().stream())
				.distinct()
				.toList();
	}

	private void replaceRedirectUris(String clientId, List<String> redirectUris) {
		clientApplicationMapper.deleteRedirectUris(clientId);
		redirectUris.forEach(redirectUri -> clientApplicationMapper.insertRedirectUri(clientId, redirectUri));
	}

	private void replaceAllowedOrigins(String clientId, List<String> allowedOrigins) {
		clientApplicationMapper.deleteAllowedOrigins(clientId);
		allowedOrigins.forEach(origin -> clientApplicationMapper.insertAllowedOrigin(clientId, origin));
	}

	private void replaceLogoutUris(String clientId, List<String> logoutUris) {
		clientApplicationMapper.deleteLogoutUris(clientId);
		logoutUris.forEach(logoutUri -> clientApplicationMapper.insertLogoutUri(clientId, logoutUri));
	}

	private void ensureExists(String clientId) {
		if (clientApplicationMapper.findByClientId(clientId).isEmpty()) {
			throw new ClientApplicationNotFoundException();
		}
	}

	private ClientApplication findExisting(String clientId) {
		return clientApplicationMapper.findByClientId(clientId)
				.orElseThrow(ClientApplicationNotFoundException::new);
	}

	private ClientMetadata validateMetadata(
			String clientId,
			String clientName,
			List<String> redirectUris,
			List<String> allowedOrigins,
			List<String> logoutUris) {
		String normalizedClientId = validateClientId(clientId);
		String normalizedClientName = requireTrimmed(clientName, MAX_CLIENT_NAME_LENGTH);
		List<String> normalizedRedirectUris = validateRedirectUris(redirectUris);
		List<String> normalizedAllowedOrigins = validateAllowedOrigins(allowedOrigins);
		List<String> normalizedLogoutUris = validateLogoutUris(logoutUris);
		return new ClientMetadata(
				normalizedClientId,
				normalizedClientName,
				normalizedRedirectUris,
				normalizedAllowedOrigins,
				normalizedLogoutUris);
	}

	private String validateClientId(String clientId) {
		String normalized = requireTrimmed(clientId, 120);
		if (!CLIENT_ID_PATTERN.matcher(normalized).matches()) {
			throw new InvalidClientMetadataException();
		}
		return normalized;
	}

	private List<String> validateRedirectUris(List<String> redirectUris) {
		if (redirectUris == null || redirectUris.isEmpty()) {
			throw new InvalidClientMetadataException();
		}
		return deduplicate(redirectUris.stream()
				.map(this::validateRedirectUri)
				.toList());
	}

	private String validateRedirectUri(String redirectUri) {
		String normalized = requireTrimmed(redirectUri, MAX_REDIRECT_URI_LENGTH);
		URI uri = parseUri(normalized);
		if (!hasHttpScheme(uri) || uri.getHost() == null || uri.getFragment() != null || uri.getUserInfo() != null) {
			throw new InvalidClientMetadataException();
		}
		return normalized;
	}

	private List<String> validateAllowedOrigins(List<String> allowedOrigins) {
		if (allowedOrigins == null) {
			return List.of();
		}
		return deduplicate(allowedOrigins.stream()
				.map(this::validateAllowedOrigin)
				.toList());
	}

	private String validateAllowedOrigin(String origin) {
		String normalized = requireTrimmed(origin, MAX_ORIGIN_LENGTH);
		URI uri = parseUri(normalized);
		if (!hasHttpScheme(uri)
				|| uri.getHost() == null
				|| uri.getUserInfo() != null
				|| uri.getRawPath() != null && !uri.getRawPath().isEmpty()
				|| uri.getRawQuery() != null
				|| uri.getFragment() != null
				|| uri.getHost().contains("*")) {
			throw new InvalidClientMetadataException();
		}
		return normalized;
	}

	private List<String> validateLogoutUris(List<String> logoutUris) {
		if (logoutUris == null) {
			return List.of();
		}
		return deduplicate(logoutUris.stream()
				.map(this::validateLogoutUri)
				.toList());
	}

	private String validateLogoutUri(String logoutUri) {
		String normalized = requireTrimmed(logoutUri, MAX_LOGOUT_URI_LENGTH);
		URI uri = parseUri(normalized);
		if (!hasHttpScheme(uri) || uri.getHost() == null || uri.getFragment() != null || uri.getUserInfo() != null) {
			throw new InvalidClientMetadataException();
		}
		return normalized;
	}

	private String requireTrimmed(String value, int maxLength) {
		if (value == null) {
			throw new InvalidClientMetadataException();
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || trimmed.length() > maxLength) {
			throw new InvalidClientMetadataException();
		}
		return trimmed;
	}

	private URI parseUri(String value) {
		try {
			return new URI(value);
		}
		catch (URISyntaxException ex) {
			throw new InvalidClientMetadataException();
		}
	}

	private boolean hasHttpScheme(URI uri) {
		String scheme = uri.getScheme();
		return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	}

	private List<String> deduplicate(List<String> values) {
		Set<String> unique = new LinkedHashSet<>(values);
		if (unique.size() != values.size()) {
			throw new InvalidClientMetadataException();
		}
		return List.copyOf(unique);
	}

	private record ClientMetadata(
			String clientId,
			String clientName,
			List<String> redirectUris,
			List<String> allowedOrigins,
			List<String> logoutUris) {
	}
}
