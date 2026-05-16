package com.centralauth.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.centralauth.user.User;
import com.centralauth.user.UserMapper;

@Service
public class AdminBootstrapService {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	private final UserMapper userMapper;
	private final List<String> bootstrapEmails;

	public AdminBootstrapService(
			UserMapper userMapper,
			@Value("${centralauth.admin.bootstrap-emails:}") String bootstrapEmails) {
		this.userMapper = userMapper;
		this.bootstrapEmails = parseEmails(bootstrapEmails);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void assignConfiguredAdmins() {
		if (bootstrapEmails.isEmpty()) {
			return;
		}

		for (String email : bootstrapEmails) {
			assignAdminRole(email);
		}
	}

	private void assignAdminRole(String email) {
		userMapper.findByEmail(email).ifPresentOrElse(
				user -> assignAdminRole(user, email),
				() -> log.warn("Admin bootstrap email was configured but no user exists: {}", email));
	}

	private void assignAdminRole(User user, String email) {
		if (userMapper.findRolesByUserId(user.id()).contains(ADMIN_ROLE)) {
			return;
		}

		userMapper.insertRole(user.id(), ADMIN_ROLE);
		log.info("Assigned ROLE_ADMIN to configured bootstrap user: {}", email);
	}

	private static List<String> parseEmails(String bootstrapEmails) {
		if (!StringUtils.hasText(bootstrapEmails)) {
			return List.of();
		}

		return Arrays.stream(bootstrapEmails.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.map(email -> email.toLowerCase(Locale.ROOT))
				.distinct()
				.toList();
	}
}
