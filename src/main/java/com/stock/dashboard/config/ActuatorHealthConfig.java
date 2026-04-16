package com.stock.dashboard.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
