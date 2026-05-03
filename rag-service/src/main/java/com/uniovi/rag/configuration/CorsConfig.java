package com.uniovi.rag.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: only applied when {@code rag.cors.allowed-origins} is non-empty (e.g. dev profile).
 * Empty default keeps production without browser CORS unless explicitly configured.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${rag.cors.allowed-origins:}") String allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (!StringUtils.hasText(allowedOrigins)) {
                    return;
                }
                String[] origins = allowedOrigins.split(",");
                for (int i = 0; i < origins.length; i++) {
                    origins[i] = origins[i].trim();
                }
                registry.addMapping("/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
