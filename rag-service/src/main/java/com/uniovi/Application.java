package com.uniovi;

import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.configuration.RagRankerProperties;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.ollama.RagOllamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
@EntityScan(basePackages = "com.uniovi.rag.infrastructure.persistence.jpa")
@EnableJpaRepositories(basePackages = "com.uniovi.rag.infrastructure.persistence")
@EnableConfigurationProperties({
    RagAccountProperties.class,
    RagApiPathProperties.class,
    RagReasoningProperties.class,
    RagRankerProperties.class,
    RagFeatureConfiguration.class,
    RagImplementationProperties.class,
    RagHealthProperties.class,
    RagOllamaProperties.class,
    RagRuntimeProperties.class
})
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
