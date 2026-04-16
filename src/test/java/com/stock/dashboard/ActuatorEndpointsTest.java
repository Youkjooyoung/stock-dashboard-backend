package com.stock.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@DisplayName("Actuator 관측성 엔드포인트 노출 확인")
class ActuatorEndpointsTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("GET /actuator/health → 200 & status=UP")
	void healthEndpointExposed() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("GET /actuator/prometheus → 200 & Prometheus 텍스트 포맷 + application 태그")
	void prometheusEndpointExposed() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("jvm_memory_used_bytes")))
				.andExpect(content().string(containsString("application=\"stock-dashboard\"")));
	}

	@Test
	@DisplayName("GET /actuator/prometheus 응답에 HTTP 요청 카운터 메트릭 존재")
	void httpServerRequestsMetricExposed() throws Exception {
		mockMvc.perform(get("/actuator/health"));

		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("http_server_requests_seconds")));
	}
}
