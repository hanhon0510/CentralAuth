package com.centralauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClientApplicationServiceTests {

	@Test
	void createClientStoresTrimmedMetadataAndRejectsDuplicateClientId() {
		FakeClientApplicationMapper mapper = new FakeClientApplicationMapper();
		ClientApplicationService service = new ClientApplicationService(mapper);

		ClientApplication created = service.createClient(
				" dashboard-app ",
				" Dashboard App ",
				List.of(" https://dashboard.example.com/auth/callback "),
				List.of(" https://dashboard.example.com "),
				true);

		assertThat(created.clientId()).isEqualTo("dashboard-app");
		assertThat(created.clientName()).isEqualTo("Dashboard App");
		assertThat(created.active()).isTrue();
		assertThat(created.redirectUris()).containsExactly("https://dashboard.example.com/auth/callback");
		assertThat(created.allowedOrigins()).containsExactly("https://dashboard.example.com");

		assertThatThrownBy(() -> service.createClient(
				"dashboard-app",
				"Duplicate",
				List.of("https://dashboard.example.com/other"),
				List.of("https://dashboard.example.com"),
				true))
				.isInstanceOf(DuplicateClientApplicationException.class);
	}

	@Test
	void createClientRejectsInvalidRedirectUris() {
		ClientApplicationService service = new ClientApplicationService(new FakeClientApplicationMapper());

		assertThatThrownBy(() -> service.createClient(
				"bad-fragment",
				"Bad Fragment",
				List.of("https://app.example.com/callback#token"),
				List.of("https://app.example.com"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);

		assertThatThrownBy(() -> service.createClient(
				"bad-relative",
				"Bad Relative",
				List.of("/callback"),
				List.of("https://app.example.com"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);

		assertThatThrownBy(() -> service.createClient(
				"bad-scheme",
				"Bad Scheme",
				List.of("javascript:alert(1)"),
				List.of("https://app.example.com"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);
	}

	@Test
	void createClientRejectsInvalidAllowedOrigins() {
		ClientApplicationService service = new ClientApplicationService(new FakeClientApplicationMapper());

		assertThatThrownBy(() -> service.createClient(
				"bad-origin-path",
				"Bad Origin Path",
				List.of("https://app.example.com/callback"),
				List.of("https://app.example.com/path"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);

		assertThatThrownBy(() -> service.createClient(
				"bad-origin-wildcard",
				"Bad Origin Wildcard",
				List.of("https://app.example.com/callback"),
				List.of("https://*.example.com"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);
	}

	@Test
	void createClientRejectsDuplicateRedirectUrisAndOriginsAfterTrimming() {
		ClientApplicationService service = new ClientApplicationService(new FakeClientApplicationMapper());

		assertThatThrownBy(() -> service.createClient(
				"duplicate-redirect",
				"Duplicate Redirect",
				List.of("https://app.example.com/callback", " https://app.example.com/callback "),
				List.of("https://app.example.com"),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);

		assertThatThrownBy(() -> service.createClient(
				"duplicate-origin",
				"Duplicate Origin",
				List.of("https://app.example.com/callback"),
				List.of("https://app.example.com", " https://app.example.com "),
				true))
				.isInstanceOf(InvalidClientMetadataException.class);
	}

	@Test
	void requireActiveClientForRedirectRejectsMissingInactiveAndMismatchedRedirects() {
		FakeClientApplicationMapper mapper = new FakeClientApplicationMapper();
		ClientApplicationService service = new ClientApplicationService(mapper);
		service.createClient(
				"dashboard-app",
				"Dashboard App",
				List.of("https://dashboard.example.com/auth/callback"),
				List.of("https://dashboard.example.com"),
				true);
		service.createClient(
				"inactive-app",
				"Inactive App",
				List.of("https://inactive.example.com/callback"),
				List.of("https://inactive.example.com"),
				false);

		assertThat(service.requireActiveClientForRedirect(
				"dashboard-app",
				"https://dashboard.example.com/auth/callback").clientId())
				.isEqualTo("dashboard-app");

		assertThatThrownBy(() -> service.requireActiveClientForRedirect(
				"dashboard-app",
				"https://evil.example.com/callback"))
				.isInstanceOf(InvalidClientMetadataException.class);
		assertThatThrownBy(() -> service.requireActiveClientForRedirect(
				"inactive-app",
				"https://inactive.example.com/callback"))
				.isInstanceOf(InactiveClientApplicationException.class);
		assertThatThrownBy(() -> service.requireActiveClientForRedirect(
				"missing-app",
				"https://missing.example.com/callback"))
				.isInstanceOf(ClientApplicationNotFoundException.class);
	}

	@Test
	void updateClientReplacesMutableMetadataAndRequiresExistingClient() {
		FakeClientApplicationMapper mapper = new FakeClientApplicationMapper();
		ClientApplicationService service = new ClientApplicationService(mapper);
		service.createClient(
				"dashboard-app",
				"Dashboard App",
				List.of("https://dashboard.example.com/auth/callback"),
				List.of("https://dashboard.example.com"),
				true);

		ClientApplication updated = service.updateClient(
				"dashboard-app",
				"Dashboard Console",
				List.of("https://console.example.com/callback"),
				List.of("https://console.example.com"),
				false);

		assertThat(updated.clientName()).isEqualTo("Dashboard Console");
		assertThat(updated.active()).isFalse();
		assertThat(updated.redirectUris()).containsExactly("https://console.example.com/callback");
		assertThat(updated.allowedOrigins()).containsExactly("https://console.example.com");

		assertThatThrownBy(() -> service.updateClient(
				"missing-app",
				"Missing",
				List.of("https://missing.example.com/callback"),
				List.of("https://missing.example.com"),
				true))
				.isInstanceOf(ClientApplicationNotFoundException.class);
	}

	@Test
	void updateActiveRequiresExistingClient() {
		FakeClientApplicationMapper mapper = new FakeClientApplicationMapper();
		ClientApplicationService service = new ClientApplicationService(mapper);
		service.createClient(
				"dashboard-app",
				"Dashboard App",
				List.of("https://dashboard.example.com/auth/callback"),
				List.of("https://dashboard.example.com"),
				true);

		assertThat(service.updateActive("dashboard-app", false).active()).isFalse();
		assertThatThrownBy(() -> service.updateActive("missing-app", false))
				.isInstanceOf(ClientApplicationNotFoundException.class);
	}

	private static final class FakeClientApplicationMapper implements ClientApplicationMapper {

		private final Map<String, StoredClient> clients = new LinkedHashMap<>();

		@Override
		public void insert(ClientApplication clientApplication) {
			clients.put(clientApplication.clientId(), new StoredClient(
					clientApplication.clientId(),
					clientApplication.clientName(),
					clientApplication.active(),
					Instant.now(),
					Instant.now(),
					new ArrayList<>(),
					new ArrayList<>()));
		}

		@Override
		public Optional<ClientApplication> findByClientId(String clientId) {
			return Optional.ofNullable(clients.get(clientId)).map(StoredClient::toClientApplication);
		}

		@Override
		public List<ClientApplication> findAll() {
			return clients.values().stream().map(StoredClient::toClientApplication).toList();
		}

		@Override
		public void insertRedirectUri(String clientId, String redirectUri) {
			clients.get(clientId).redirectUris().add(redirectUri);
		}

		@Override
		public void deleteRedirectUris(String clientId) {
			clients.get(clientId).redirectUris().clear();
		}

		@Override
		public void insertAllowedOrigin(String clientId, String origin) {
			clients.get(clientId).allowedOrigins().add(origin);
		}

		@Override
		public void deleteAllowedOrigins(String clientId) {
			clients.get(clientId).allowedOrigins().clear();
		}

		@Override
		public int updateClient(String clientId, String clientName, boolean active) {
			StoredClient current = clients.get(clientId);
			if (current == null) {
				return 0;
			}
			clients.put(clientId, current.withMetadata(clientName, active));
			return 1;
		}

		@Override
		public int updateActive(String clientId, boolean active) {
			StoredClient current = clients.get(clientId);
			if (current == null) {
				return 0;
			}
			clients.put(clientId, current.withMetadata(current.clientName(), active));
			return 1;
		}

	}

	private record StoredClient(
			String clientId,
			String clientName,
			boolean active,
			Instant createdAt,
			Instant updatedAt,
			List<String> redirectUris,
			List<String> allowedOrigins) {

		ClientApplication toClientApplication() {
			return new ClientApplication(
					clientId,
					clientName,
					active,
					createdAt,
					updatedAt,
					List.copyOf(redirectUris),
					List.copyOf(allowedOrigins));
		}

		StoredClient withMetadata(String nextClientName, boolean nextActive) {
			return new StoredClient(
					clientId,
					nextClientName,
					nextActive,
					createdAt,
					Instant.now(),
					new ArrayList<>(redirectUris),
					new ArrayList<>(allowedOrigins));
		}
	}
}
