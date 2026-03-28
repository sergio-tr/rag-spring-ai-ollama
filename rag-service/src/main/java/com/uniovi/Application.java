package com.uniovi;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.configuration.RagRankerProperties;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.health.RagHealthProperties;
import com.uniovi.rag.ollama.RagOllamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    RagReasoningProperties.class,
    RagRankerProperties.class,
    RagFeatureConfiguration.class,
    RagImplementationProperties.class,
    RagHealthProperties.class,
    RagOllamaProperties.class
})
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
