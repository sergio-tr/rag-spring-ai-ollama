package com.uniovi.rag.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strict JSON for P31 regression-suite POST bodies: unknown properties fail deserialization (isolated from other REST
 * surfaces).
 */
@Configuration
public class RegressionSuiteRestJacksonConfiguration {

    public static final String REGRESSION_SUITE_STRICT_OBJECT_MAPPER = "regressionSuiteStrictObjectMapper";

    @Bean(name = REGRESSION_SUITE_STRICT_OBJECT_MAPPER)
    public ObjectMapper regressionSuiteStrictObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
