package com.centralauth;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.centralauth.health.HealthController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CentralAuthApplicationTests {

	@Test
	void healthEndpointReturnsApplicationStatus() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.application").value("CentralAuth"))
				.andExpect(jsonPath("$.status").value("UP"));
	}

}
