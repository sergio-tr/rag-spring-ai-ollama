package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.configuration.RagImplementationProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Test-only bootstrap in {@code com.uniovi.rag.interfaces.rest.support} so slice tests resolve this before
 * {@link com.uniovi.Application} (avoids JPA/Flyway) while keeping web auto-configuration.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableConfigurationProperties({RagApiPathProperties.class, RagImplementationProperties.class})
@TestPropertySource(
        properties = {
            "rag.api.product-base-path=/api/v5",
            "rag.api.legacy-base-path=/api/v4"
        })
public class RagWebMvcTestApplication {
}
