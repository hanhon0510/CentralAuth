package com.centralauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoClientBootstrapServiceTests {

	@Test
	void registersMissingDemoClients() {
		ClientApplicationService clientApplicationService = mock(ClientApplicationService.class);
		when(clientApplicationService.listClients()).thenReturn(List.of());
		DemoClientBootstrapService bootstrapService = new DemoClientBootstrapService(
				clientApplicationService,
				true);

		bootstrapService.registerDemoClients();

		ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> clientNameCaptor = ArgumentCaptor.forClass(String.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> redirectUrisCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> allowedOriginsCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> logoutUrisCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<Boolean> activeCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(clientApplicationService, times(2)).createClient(
				clientIdCaptor.capture(),
				clientNameCaptor.capture(),
				redirectUrisCaptor.capture(),
				allowedOriginsCaptor.capture(),
				logoutUrisCaptor.capture(),
				activeCaptor.capture());

		assertThat(clientIdCaptor.getAllValues()).containsExactly("projects-demo", "reports-demo");
		assertThat(clientNameCaptor.getAllValues())
				.containsExactly("Projects Demo Client", "Reports Demo Client");
		assertThat(activeCaptor.getAllValues()).containsExactly(true, true);
		assertThat(redirectUrisCaptor.getAllValues().get(0))
				.contains(
						"http://localhost:5173/demo/projects/callback",
						"http://127.0.0.1:5175/demo/projects/callback");
		assertThat(redirectUrisCaptor.getAllValues().get(1))
				.contains(
						"http://localhost:5173/demo/reports/callback",
						"http://127.0.0.1:5175/demo/reports/callback");
		assertThat(allowedOriginsCaptor.getAllValues().get(0))
				.contains("http://localhost:5173", "http://127.0.0.1:5175");
		assertThat(logoutUrisCaptor.getAllValues().get(0))
				.contains(
						"http://localhost:5173/demo/projects/logout",
						"http://127.0.0.1:5175/demo/projects/logout");
		assertThat(logoutUrisCaptor.getAllValues().get(1))
				.contains(
						"http://localhost:5173/demo/reports/logout",
						"http://127.0.0.1:5175/demo/reports/logout");
	}

	@Test
	void skipsExistingDemoClientsWithoutOverwritingThem() {
		ClientApplicationService clientApplicationService = mock(ClientApplicationService.class);
		when(clientApplicationService.listClients()).thenReturn(List.of(existingProjectsClient()));
		DemoClientBootstrapService bootstrapService = new DemoClientBootstrapService(
				clientApplicationService,
				true);

		bootstrapService.registerDemoClients();

		verify(clientApplicationService, never()).createClient(
				eq("projects-demo"),
				anyString(),
				any(),
				any(),
				any(),
				anyBoolean());
		verify(clientApplicationService).createClient(
				eq("reports-demo"),
				eq("Reports Demo Client"),
				any(),
				any(),
				any(),
				eq(true));
	}

	@Test
	void doesNotRegisterDemoClientsWhenDisabled() {
		ClientApplicationService clientApplicationService = mock(ClientApplicationService.class);
		DemoClientBootstrapService bootstrapService = new DemoClientBootstrapService(
				clientApplicationService,
				false);

		bootstrapService.registerDemoClients();

		verifyNoInteractions(clientApplicationService);
	}

	private ClientApplication existingProjectsClient() {
		return new ClientApplication(
				"projects-demo",
				"Admin Edited Projects Client",
				true,
				Instant.now(),
				Instant.now(),
				List.of("https://projects.example.com/callback"),
				List.of("https://projects.example.com"),
				List.of("https://projects.example.com/logout"));
	}
}
