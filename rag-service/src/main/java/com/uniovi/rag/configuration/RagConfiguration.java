package com.uniovi.rag.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    RagRetrievalConfiguration.class,
    RagToolsBeanConfiguration.class,
    RagDocumentConfiguration.class,
    RagQueryConfiguration.class,
    RagEvaluationConfiguration.class
})
public class RagConfiguration {

    @Bean
    public RagFeatureConfiguration featureConfig() {
        return new RagFeatureConfiguration();
    }

    @Bean
    public int maxChunkSize(@Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
        if (chunkMaxChars > 0) {
            return chunkMaxChars;
        }
        return 400;
    }

    @Bean
    public boolean cleanBeforeLoad(@Value("${evaluation.clean-before-load:true}") boolean cleanBeforeLoad) {
        return cleanBeforeLoad;
    }

    /**
     * Single ChatClient with no tools/advisor to avoid circular dependency.
     * ProcessQueryService applies tools and QuestionAnswerAdvisor at call time via .tools() and .advisors().
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
