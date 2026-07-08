package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelGovernanceServiceTest {

    @Mock private ModelCatalogPort modelCatalogPort;

    private LlmModelCatalogPort llmModelCatalogPort;
    private ModelGovernanceService service;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b", "deepseek-v2:16b", "openai-chat-b", "deepseek-r1:1.5b"));
        openAi.setDefaultEmbeddingModel("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        openAi.setAvailableEmbeddingModels(List.of(
                "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest", "mxbai-embed-large", "bge-m3", "snowflake-arctic-embed2"));
        llmModelCatalogPort = new LlmModelCatalogService(properties);
        service = new ModelGovernanceService(modelCatalogPort, llmModelCatalogPort);
        when(modelCatalogPort.blockedLlmNamesInGovernance()).thenReturn(Set.of());
        when(modelCatalogPort.blockedEmbeddingNamesInGovernance()).thenReturn(Set.of());
    }

    @Test
    void propertiesModelAcceptedWithoutDbRow() {
        service.assertChatModelAllowed(LlmProvider.OPENAI_COMPATIBLE, "deepseek-v2:16b");
        assertThat(service.isChatModelGovernanceAllowed(LlmProvider.OPENAI_COMPATIBLE, "deepseek-v2:16b"))
                .isTrue();
    }

    @Test
    void explicitlyBlockedModelRejected() {
        when(modelCatalogPort.blockedLlmNamesInGovernance()).thenReturn(Set.of("deepseek-v2:16b"));

        assertThatThrownBy(
                        () -> service.assertChatModelAllowed(LlmProvider.OPENAI_COMPATIBLE, "deepseek-v2:16b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked by governance");
    }

    @Test
    void unknownModelRejected() {
        assertThatThrownBy(
                        () -> service.assertChatModelAllowed(LlmProvider.OPENAI_COMPATIBLE, "unknown-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed by governance");
    }

    @Test
    void embeddingModelUsesEmbeddingBlocklist() {
        when(modelCatalogPort.blockedEmbeddingNamesInGovernance())
                .thenReturn(Set.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));

        assertThatThrownBy(
                        () ->
                                service.assertEmbeddingModelAllowed(
                                        LlmProvider.OPENAI_COMPATIBLE,
                                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked by governance");
    }

    @Test
    void catalogLookupUsesProviderScope() {
        Optional<LlmCatalogEntry> entry =
                llmModelCatalogPort.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.CHAT);
        assertThat(entry).isPresent();
        assertThat(entry.get().source()).isEqualTo(LlmCatalogSource.LITELLM_CONFIGURED);
        assertThat(service.isKnownChatModel(LlmProvider.OLLAMA_NATIVE, "gpt-oss:20b")).isFalse();
    }

    @Test
    void bgeM3_notBlockedWhenAbsentFromBlocklist() {
        when(modelCatalogPort.blockedEmbeddingNamesInGovernance()).thenReturn(Set.of());

        assertThat(service.isEmbeddingModelGovernanceAllowed(LlmProvider.OPENAI_COMPATIBLE, "bge-m3"))
                .isTrue();
        assertThat(service.isEmbeddingModelBlocked("bge-m3")).isFalse();
    }
}
