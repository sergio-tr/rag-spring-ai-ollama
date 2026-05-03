package com.uniovi.rag.configuration;

import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigurationContextTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withPropertyValues("spring.main.web-application-type=servlet")
                    .withConfiguration(
                            AutoConfigurations.of(SecurityAutoConfiguration.class, WebMvcAutoConfiguration.class))
                    .withUserConfiguration(SecurityHarness.class, SecurityConfiguration.class);

    @Test
    void loadsSecurityFilterChainPasswordEncoderAndCors() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
                    assertThat(ctx).hasSingleBean(PasswordEncoder.class);
                    Map<String, CorsConfigurationSource> corsBeans =
                            ctx.getBeansOfType(CorsConfigurationSource.class);
                    assertThat(corsBeans).containsKey("corsConfigurationSource");
                });
    }

    @Configuration
    @EnableConfigurationProperties(RagApiPathProperties.class)
    static class SecurityHarness {

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
            return new JwtAuthenticationFilter(jwtService);
        }
    }
}
