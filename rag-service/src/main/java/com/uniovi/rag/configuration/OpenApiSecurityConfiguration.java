package com.uniovi.rag.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI security metadata for Swagger UI authorization (Bearer JWT).
 *
 * <p>HTTP security is enforced by Spring Security; this configuration is documentation-only and must
 * never be used as an authorization mechanism.
 */
@Configuration
@OpenAPIDefinition
@SecurityScheme(
        name = OpenApiSecurityConfiguration.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
public class OpenApiSecurityConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * Apply BearerAuth security requirement to protected route families in the generated OpenAPI.
     *
     * <p>Rules:
     * - `${rag.api.product-base-path}/**`: authenticated product API
     * - `/api/admin/**`: admin-only API
     * - `/api/auth/**` (others): public
     */
    @Bean
    public OpenApiCustomizer applyBearerAuthToProtectedPaths(RagApiPathProperties ragApiPathProperties) {
        SecurityRequirement bearer = new SecurityRequirement().addList(BEARER_AUTH);
        String productBasePath = ragApiPathProperties != null ? ragApiPathProperties.getProductBasePath() : "/api/v5";
        return (OpenAPI openApi) -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> {
                boolean isProductAuthPath =
                        path != null
                                && (path.equals(productBasePath + "/auth")
                                        || path.startsWith(productBasePath + "/auth/"));
                boolean secured =
                        path != null
                                && (path.startsWith(productBasePath + "/")
                                        || path.startsWith("/api/admin/"))
                                && !isProductAuthPath;
                if (!secured || item == null) return;
                item.readOperations().forEach(op -> {
                    if (op.getSecurity() == null || op.getSecurity().isEmpty()) {
                        op.addSecurityItem(bearer);
                    }
                });
            });
        };
    }
}
