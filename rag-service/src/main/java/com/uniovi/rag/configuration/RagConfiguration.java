package com.uniovi.rag.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(RagVectorProperties.class)
@Import({
    RagRetrievalConfiguration.class,
    RagToolsBeanConfiguration.class,
    RagDocumentConfiguration.class,
    RagQueryConfiguration.class,
    RagEvaluationConfiguration.class
})
public class RagConfiguration {

    /**
     * Single ChatClient with no tools/advisor to avoid circular dependency.
     * ProcessQueryService applies tools and QuestionAnswerAdvisor at call time via .tools() and .advisors().
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        You are a helpful assistant. Always answer the user's message directly in the same language they use.
                        When document context is provided below, use it for facts about those documents; otherwise use general knowledge.
                        Never ask the user to provide context, conversation history, or retrieved fragments unless they are debugging the system.""")
                .build();
    }
}
