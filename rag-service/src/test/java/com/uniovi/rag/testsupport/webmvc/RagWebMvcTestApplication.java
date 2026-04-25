package com.uniovi.rag.testsupport.webmvc;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.configuration.RagImplementationProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;

/**
 * Slice-test bootstrap for {@code @WebMvcTest}. Lives in {@code com.uniovi.rag.testsupport.webmvc} so the
 * production {@link com.uniovi.Application} component scan can exclude this package: if this class stayed under
 * {@code com.uniovi}, full {@code @SpringBootTest} runs would pick it up from the test classpath and merge its
 * {@code @SpringBootApplication(exclude = ...)} (disabling JPA / DataSource auto-config for every test).
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
@TestPropertySource(properties = "rag.api.legacy-base-path=/api/v4")
public class RagWebMvcTestApplication {
}
