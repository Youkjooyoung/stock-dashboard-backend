package com.stock.dashboard.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

	private static final String CACHE_LATEST_PRICES   = "latestPrices";
	private static final String CACHE_ALL_ITEMS       = "allItems";
	private static final String CACHE_PRICE_BY_TICKER = "priceByTicker";

	private static final Duration TTL_LATEST_PRICES   = Duration.ofMinutes(10);
	private static final Duration TTL_ALL_ITEMS       = Duration.ofHours(1);
	private static final Duration TTL_PRICE_BY_TICKER = Duration.ofMinutes(30);
	private static final Duration TTL_DEFAULT         = Duration.ofMinutes(5);

	@Bean
	@ConditionalOnMissingBean(CacheManager.class)
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
	public CacheManager inMemoryCacheManager() {
		return new ConcurrentMapCacheManager(
				CACHE_LATEST_PRICES, CACHE_ALL_ITEMS, CACHE_PRICE_BY_TICKER);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
	public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
		Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
		perCache.put(CACHE_LATEST_PRICES,   baseConfig(TTL_LATEST_PRICES));
		perCache.put(CACHE_ALL_ITEMS,       baseConfig(TTL_ALL_ITEMS));
		perCache.put(CACHE_PRICE_BY_TICKER, baseConfig(TTL_PRICE_BY_TICKER));

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(baseConfig(TTL_DEFAULT))
				.withInitialCacheConfigurations(perCache)
				.build();
	}

	private RedisCacheConfiguration baseConfig(Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				.prefixCacheNameWith("stock-dashboard::")
				.serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
	}
}
