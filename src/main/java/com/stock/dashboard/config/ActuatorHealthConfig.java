package com.stock.dashboard.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redis 클라이언트(lettuce) 관련 지표를 /actuator/prometheus 에서 뺀다. */
@Configuration
public class ActuatorHealthConfig {

	@Bean
	public MeterFilter denyLettuceMetrics() {
		return MeterFilter.deny(id -> {
			String name = id.getName();
			return name.startsWith("lettuce.")
					|| name.startsWith("redis.")
					|| name.startsWith("spring.data.redis.");
		});
	}
}
