package com.umar.ecommerce.catalog.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umar.ecommerce.catalog.config.CatalogCacheProperties;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Public catalog cache. Values are explicit DTO types. There is no default typing
 * and no JPA entity in Redis. Each region adds a small random TTL so keys do not
 * expire on the same instant.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CatalogCacheConfig implements CachingConfigurer {

    private final CatalogCacheProperties properties;
    private final CatalogCacheErrorHandler errorHandler;

    public CatalogCacheConfig(CatalogCacheProperties properties, CatalogCacheErrorHandler errorHandler) {
        this.properties = properties;
        this.errorHandler = errorHandler;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return errorHandler;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    CacheManager redisCacheManager(org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = cacheMapper();
        RedisCacheManager redis = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(region(mapper, mapper.constructType(Object.class), properties.productsTtl()))
                .withCacheConfiguration(PublicCacheNames.CATEGORIES, region(
                        mapper,
                        mapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class),
                        properties.categoriesTtl()
                ))
                .withCacheConfiguration(PublicCacheNames.PRODUCTS, region(
                        mapper,
                        mapper.getTypeFactory().constructParametricType(PageResponse.class, ProductSummaryResponse.class),
                        properties.productsTtl()
                ))
                .withCacheConfiguration(PublicCacheNames.PRODUCT_ID, region(
                        mapper,
                        mapper.constructType(ProductResponse.class),
                        properties.productTtl()
                ))
                .withCacheConfiguration(PublicCacheNames.PRODUCT_SLUG, region(
                        mapper,
                        mapper.constructType(ProductResponse.class),
                        properties.productTtl()
                ))
                .withCacheConfiguration(PublicCacheNames.VARIANTS, region(
                        mapper,
                        mapper.getTypeFactory().constructCollectionType(List.class, ProductVariantResponse.class),
                        properties.variantsTtl()
                ))
                .build();
        redis.afterPropertiesSet();
        return new CoalescingCacheManager(redis);
    }

    private RedisCacheConfiguration region(ObjectMapper mapper, JavaType type, Duration base) {
        return common(base).serializeValuesWith(SerializationPair.fromSerializer(new TypedJson(mapper, type)));
    }

    private RedisCacheConfiguration common(Duration base) {
        Duration jitter = properties.ttlJitter() == null ? Duration.ZERO : properties.ttlJitter();
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .entryTtl(new JitteredTtl(base, jitter.toMillis()))
                .disableCachingNullValues();
    }

    static ObjectMapper cacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        return mapper;
    }

    /**
     * RedisCacheConfiguration requires a serializable TTL function. The extra
     * milliseconds are chosen when the entry is written, per key, on this instance.
     */
    /**
     * Reads and writes one declared DTO type. The cache manager is initialized
     * before it is wrapped so these per-region types are the ones Redis uses.
     */
    static final class TypedJson implements RedisSerializer<Object> {

        private final ObjectMapper mapper;
        private final JavaType type;

        TypedJson(ObjectMapper mapper, JavaType type) {
            this.mapper = mapper;
            this.type = type;
        }

        @Override
        public byte[] serialize(Object value) {
            if (value == null) {
                return new byte[0];
            }
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception exception) {
                throw new SerializationException("Could not write " + type, exception);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            try {
                return mapper.readValue(bytes, type);
            } catch (Exception exception) {
                throw new SerializationException("Could not read " + type, exception);
            }
        }
    }

    static final class JitteredTtl implements RedisCacheWriter.TtlFunction {

        private final Duration base;
        private final long jitterMillis;

        JitteredTtl(Duration base, long jitterMillis) {
            this.base = base == null ? Duration.ofSeconds(45) : base;
            this.jitterMillis = Math.max(0, jitterMillis);
        }

        @Override
        public Duration getTimeToLive(Object key, Object value) {
            if (jitterMillis == 0) {
                return base;
            }
            return base.plusMillis(ThreadLocalRandom.current().nextLong(jitterMillis + 1));
        }
    }
}
