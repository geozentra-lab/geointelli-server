package com.geointelli.ai.notification.service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boot 4 uses Jackson 3; accepting additive fields does not permit scalar
 * coercion or ambiguous JSON.
 */
@Configuration
public class NotificationJsonConfig {
    /**
     * REST requests reject unknown fields; queue events still allow additive
     * contract fields.
     */
    @Bean("notificationApiMapper")
    @Primary
    public JsonMapper notificationApiMapper() {
        return notificationEventMapper().rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Bean("notificationEventMapper")
    public JsonMapper notificationEventMapper() {
        return JsonMapper.builder(JsonFactory.builder().streamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(50).maxStringLength(8192).build()).build())
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    @Bean("notificationClock")
    public Clock notificationClock() {
        return Clock.systemUTC();
    }
}
