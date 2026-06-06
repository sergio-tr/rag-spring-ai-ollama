package com.uniovi.rag.configuration;

import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.RestAccessDeniedHandler;
import com.uniovi.rag.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT stateless security for the product API prefix ({@link RagApiPathProperties#getProductBasePath()});
 * actuator and auth endpoints are public.
 *
 * <p><strong>CSRF:</strong> Spring Security enables CSRF by default for cookie-backed sessions. This application
 * uses {@link SessionCreationPolicy#STATELESS} and authenticates API calls via {@code Authorization: Bearer &lt;jwt&gt;}
 * ({@link com.uniovi.rag.security.JwtAuthenticationFilter}). The browser does not attach that header to cross-site
 * requests automatically (unlike session cookies), so classic CSRF against “logged-in browser sessions” does not apply.
 * Disabling CSRF follows Spring Security guidance for non-browser clients / token APIs; if cookie-based login were added
 * later, CSRF would need to be re-evaluated for those endpoints.
 *
 * @see <a href="https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html">Spring Security CSRF</a>
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            CorsConfigurationSource corsConfigurationSource,
            RagApiPathProperties ragApiPathProperties,
            RequestMappingHandlerMapping requestMappingHandlerMapping)
            throws Exception {
        String productBasePath = ragApiPathProperties.getProductBasePath();
        String productAuthBase = productBasePath + "/auth";
        http
                // Stateless Bearer JWT — see class Javadoc (CSRF targets automatic cookie submission).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(requestMappingHandlerMapping))
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .authorizeHttpRequests(a -> a
                        // New primary auth contract under product API base path.
                        .requestMatchers(HttpMethod.GET, productAuthBase + "/me").authenticated()
                        .requestMatchers(
                                productAuthBase + "/login",
                                productAuthBase + "/register",
                                productAuthBase + "/public-config",
                                productAuthBase + "/refresh",
                                productAuthBase + "/confirm-email",
                                productAuthBase + "/resend-confirmation",
                                productAuthBase + "/forgot-password",
                                productAuthBase + "/reset-password",
                                productAuthBase + "/oauth/google/start",
                                productAuthBase + "/oauth/google/callback",
                                productAuthBase + "/oauth/exchange")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(productBasePath + "/admin/**").hasRole("ADMIN")
                        .requestMatchers(productBasePath + "/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CorsConfigurationSource corsConfigurationSource(
            @Value(
                            "${rag.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,"
                                    + "https://localhost:*,https://127.0.0.1:*}")
                    String allowedOrigins) {
        return buildCors(allowedOrigins);
    }

    private static CorsConfigurationSource buildCors(String allowedOrigins) {
        List<String> patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (patterns.isEmpty()) {
            patterns = List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "https://localhost:*",
                    "https://127.0.0.1:*");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(patterns);
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addAllowedMethod(CorsConfiguration.ALL);
        config.addExposedHeader("Content-Type");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
