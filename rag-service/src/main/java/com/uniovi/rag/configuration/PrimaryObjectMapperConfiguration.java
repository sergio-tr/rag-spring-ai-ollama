package com.uniovi.rag.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Application-wide {@link ObjectMapper} for HTTP JSON and general injection. Several strict, named
 * {@link ObjectMapper} beans exist for isolated REST surfaces; they prevent Spring Boot from registering its default
 * {@code @ConditionalOnMissingBean(ObjectMapper)} bean, so unqualified {@link ObjectMapper} injection would otherwise
 * fail with multiple candidates.
 */
@Configuration
public class PrimaryObjectMapperConfiguration {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false).build();
    }
}
