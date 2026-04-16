package com.stock.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@DisplayName("CacheConfig 프로파일 분기 테스트")
class CacheConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(CacheConfig.class);

	@Nested
	@DisplayName("기본 / simple 프로파일")
	class SimpleProfile {

		@Test
		@DisplayName("spring.cache.type 미지정 시 ConcurrentMapCacheManager 주입")
		void whenPropertyMissing_thenInMemoryCacheManager() {
			runner.run(ctx -> {
				CacheManager cacheManager = ctx.getBean(CacheManager.class);
				assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
				assertThat(cacheManager.getCacheNames())
						.containsExactlyInAnyOrder("latestPrices", "allItems", "priceByTicker");
			});
		}

		@Test
		@DisplayName("spring.cache.type=simple 명시 시 ConcurrentMapCacheManager 주입")
		void whenSimple_thenInMemoryCacheManager() {
			runner.withPropertyValues("spring.cache.type=simple")
					.run(ctx -> {
						CacheManager cacheManager = ctx.getBean(CacheManager.class);
						assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
					});
		}
	}

	@Nested
	@DisplayName("redis 프로파일")
	class RedisProfile {

		@Test
		@DisplayName("spring.cache.type=redis 지정 시 RedisCacheManager 주입")
		void whenRedis_thenRedisCacheManager() {
			new ApplicationContextRunner()
					.withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
					.withUserConfiguration(CacheConfig.class, MockRedisConnectionConfig.class)
					.withPropertyValues("spring.cache.type=redis")
					.run(ctx -> {
						CacheManager cacheManager = ctx.getBean(CacheManager.class);
						assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
					});
		}
	}

	@Configuration
	static class MockRedisConnectionConfig {
		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			return mock(RedisConnectionFactory.class);
		}
	}
}
