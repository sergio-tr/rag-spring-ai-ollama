package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CorsConfigTest {

    @Test
    void emptyAllowedOriginsSkipsRegistration() {
        CorsConfig cfg = new CorsConfig();
        WebMvcConfigurer mvc = cfg.corsConfigurer("");
        assertDoesNotThrow(() -> mvc.addCorsMappings(new CorsRegistry()));
    }

    @Test
    void nonEmptyAllowedOriginsRegistersMappings() {
        CorsConfig cfg = new CorsConfig();
        WebMvcConfigurer mvc = cfg.corsConfigurer("http://localhost:3000, http://localhost:4000 ");
        assertDoesNotThrow(() -> mvc.addCorsMappings(new CorsRegistry()));
    }
}
