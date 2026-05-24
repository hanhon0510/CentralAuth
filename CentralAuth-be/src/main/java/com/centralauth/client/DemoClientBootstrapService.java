package com.centralauth.client;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class DemoClientBootstrapService {

	private static final List<String> LOCAL_DEMO_ORIGINS = List.of(
			"http://localhost:5173",
			"http://localhost:5174",
			"http://localhost:5175",
			"http://127.0.0.1:5173",
			"http://127.0.0.1:5174",
			"http://127.0.0.1:5175");

	private static final List<DemoClientRegistration> DEMO_CLIENTS = List.of(
			new DemoClientRegistration(
					"projects-demo",
					"Projects Demo Client",
					"/demo/projects/callback"),
			new DemoClientRegistration(
					"reports-demo",
					"Reports Demo Client",
					"/demo/reports/callback"));

	private final ClientApplicationService clientApplicationService;
	private final boolean enabled;

	public DemoClientBootstrapService(
			ClientApplicationService clientApplicationService,
			@Value("${centralauth.demo-clients.enabled:true}") boolean enabled) {
		this.clientApplicationService = clientApplicationService;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void registerDemoClients() {
		if (!enabled) {
			return;
		}

		Set<String> existingClientIds = clientApplicationService.listClients().stream()
				.map(ClientApplication::clientId)
				.collect(java.util.stream.Collectors.toSet());

		for (DemoClientRegistration demoClient : DEMO_CLIENTS) {
			if (existingClientIds.contains(demoClient.clientId())) {
				continue;
			}

			clientApplicationService.createClient(
					demoClient.clientId(),
					demoClient.clientName(),
					demoClient.redirectUris(),
					LOCAL_DEMO_ORIGINS,
					true);
		}
	}

	private record DemoClientRegistration(
			String clientId,
			String clientName,
			String callbackPath) {

		List<String> redirectUris() {
			return LOCAL_DEMO_ORIGINS.stream()
					.map(origin -> origin + callbackPath)
					.toList();
		}
	}
}
