package com.uniovi.rag.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strict JSON for P25 batch POST bodies: unknown properties fail deserialization (tests rely on this).
 */
@Configuration
public class TraceComparisonBatchRestJacksonConfiguration {

    public static final String TRACE_COMPARISON_BATCH_STRICT_OBJECT_MAPPER = "traceComparisonBatchStrictObjectMapper";

    @Bean(name = TRACE_COMPARISON_BATCH_STRICT_OBJECT_MAPPER)
    public ObjectMapper traceComparisonBatchStrictObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
