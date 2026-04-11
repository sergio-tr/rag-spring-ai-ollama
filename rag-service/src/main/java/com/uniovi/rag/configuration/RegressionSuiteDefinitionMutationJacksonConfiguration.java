package com.uniovi.rag.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strict JSON for P35 suite-definition mutation bodies (POST/PUT): unknown properties fail deserialization; isolated
 * from the application default {@link ObjectMapper}.
 */
@Configuration
public class RegressionSuiteDefinitionMutationJacksonConfiguration {

    public static final String DEFINITION_MUTATION_STRICT_OBJECT_MAPPER = "definitionMutationStrictObjectMapper";

    @Bean(name = DEFINITION_MUTATION_STRICT_OBJECT_MAPPER)
    public ObjectMapper definitionMutationStrictObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
