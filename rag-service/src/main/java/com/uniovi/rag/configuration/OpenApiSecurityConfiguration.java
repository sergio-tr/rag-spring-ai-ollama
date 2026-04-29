package com.uniovi.rag.configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds JWT bearer authentication metadata to the generated OpenAPI document.
 *
 * <p>Runtime security is enforced by Spring Security; this configuration is for documentation/Swagger UI.
 */
@Configuration
@SecurityScheme(
        name = OpenApiSecurityConfiguration.BEARER_SCHEME_NAME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
public class OpenApiSecurityConfiguration {

    public static final String BEARER_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenApiCustomizer applyBearerAuthToProtectedPaths() {
        return (OpenAPI openApi) -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (Map.Entry<String, io.swagger.v3.oas.models.PathItem> e : openApi.getPaths().entrySet()) {
                String path = e.getKey();
                if (!path.startsWith("/api/")) {
                    continue;
                }
                if (path.startsWith("/api/auth/")) {
                    continue; // auth endpoints are public
                }
                io.swagger.v3.oas.models.PathItem item = e.getValue();
                if (item == null) continue;
                item.readOperations().forEach(op -> {
                    if (op.getSecurity() == null || op.getSecurity().isEmpty()) {
                        op.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
                    }
                });
            }
        };
    }
}

