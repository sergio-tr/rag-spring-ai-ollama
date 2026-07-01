package com.uniovi.rag.application.service.runtime.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelRoleResolver;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagChatModelRoutingServiceTest {

    @Test
    void glmOcrFallsBackToSafePrimaryChatModel() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties
                .getOpenAiCompatible()
                .setAvailableChatModels(List.of("glm-ocr:latest", "gpt-oss:20b"));
        properties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        RagChatModelRoutingService routing = new RagChatModelRoutingService(catalog);

        RagChatModelRoutingService.RoutedChatModel routed =
                routing.resolvePrimary(LlmProvider.OPENAI_COMPATIBLE, "glm-ocr:latest", null);

        assertThat(routed.fallbackApplied()).isTrue();
        assertThat(routed.requestedModel()).isEqualTo("glm-ocr:latest");
        assertThat(routed.model()).isEqualTo("gpt-oss:20b");
    }

    @Test
    void primaryChatModelPassesThroughWhenCapable() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
        RagChatModelRoutingService routing = new RagChatModelRoutingService(catalog);

        RagChatModelRoutingService.RoutedChatModel routed =
                routing.resolvePrimary(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", null);

        assertThat(routed.fallbackApplied()).isFalse();
        assertThat(routed.model()).isEqualTo("gpt-oss:20b");
    }

    @Test
    void ocrModelIsNotPrimaryCapable() {
        assertThat(LlmModelRoleResolver.supportsPrimaryChat("glm-ocr:latest", LlmModelCapability.CHAT))
                .isFalse();
    }

    @Test
    void blocksWhenNoSafeFallbackExists() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenAiCompatible().setDefaultBaseUrl("http://litellm:4000");
        properties.getOpenAiCompatible().setAvailableChatModels(List.of("glm-ocr:latest"));
        properties.getOpenAiCompatible().setDefaultChatModel("glm-ocr:latest");
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        RagChatModelRoutingService routing = new RagChatModelRoutingService(catalog);

        assertThrows(
                Exception.class,
                () -> routing.resolvePrimary(LlmProvider.OPENAI_COMPATIBLE, "glm-ocr:latest", null));
    }
}
