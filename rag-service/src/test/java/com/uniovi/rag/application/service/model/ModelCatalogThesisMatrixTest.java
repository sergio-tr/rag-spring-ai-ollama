package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards thesis evaluation model matrix visibility in the OPENAI_COMPATIBLE properties catalog.
 */
class ModelCatalogThesisMatrixTest {

    private static final List<String> THESIS_EMBEDDING_MODELS = List.of(
            "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
            "mxbai-embed-large",
            "bge-m3",
            "snowflake-arctic-embed2");

    private static final List<String> THESIS_LLM_SMALL = List.of(
            "deepseek-r1:1.5b",
            "llama3.2:3b",
            "qwen3.5:2b",
            "qwen3.5:4b",
            "gemma4:e2b",
            "gemma4:e4b");

    private static final List<String> THESIS_LLM_SMALL_MEDIUM = List.of("deepseek-r1:7b", "gemma4:12b", "qwen3.5:9b");

    private static final List<String> THESIS_LLM_MEDIUM =
            List.of("deepseek-v2:16b", "gpt-oss:20b", "gemma4:26b", "qwen3.6:27b");

    private LlmModelCatalogService catalogService;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultChatModel("qwen3.6:35b");
        openAi.setDefaultEmbeddingModel("bge-m3");
        openAi.setAvailableChatModels(concat(
                THESIS_LLM_SMALL, THESIS_LLM_SMALL_MEDIUM, THESIS_LLM_MEDIUM, List.of("qwen3.6:35b")));
        openAi.setAvailableEmbeddingModels(THESIS_EMBEDDING_MODELS);
        catalogService = new LlmModelCatalogService(properties);
    }

    @Test
    void thesisEmbeddingModels_areInOpenAiCompatibleCatalog() {
        Set<String> configured = catalogService
                .listConfigured(LlmCatalogQuery.forProviderAndCapability(
                        LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.EMBEDDING))
                .stream()
                .map(e -> e.modelName())
                .collect(Collectors.toSet());
        assertThat(configured).containsAll(THESIS_EMBEDDING_MODELS);
    }

    @Test
    void thesisLlmGroups_areInOpenAiCompatibleCatalog() {
        Set<String> configured = catalogService
                .listConfigured(
                        LlmCatalogQuery.forProviderAndCapability(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT))
                .stream()
                .map(e -> e.modelName())
                .collect(Collectors.toSet());
        assertThat(configured)
                .containsAll(THESIS_LLM_SMALL)
                .containsAll(THESIS_LLM_SMALL_MEDIUM)
                .containsAll(THESIS_LLM_MEDIUM);
    }

    @Test
    void gemma4e2b_isInSmallTierCatalog() {
        Set<String> configured = catalogService
                .listConfigured(
                        LlmCatalogQuery.forProviderAndCapability(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT))
                .stream()
                .map(e -> e.modelName())
                .collect(Collectors.toSet());
        assertThat(configured).contains("gemma4:e2b");
    }

    @Test
    void thesisEmbeddings_passStoreCompatibilityFilter() {
        EmbeddingModelStoreCompatibility compatibility =
                new EmbeddingModelStoreCompatibility(new RagVectorProperties(1024, true));
        assertThat(THESIS_EMBEDDING_MODELS)
                .allSatisfy(id -> assertThat(compatibility.isSelectableForLabEmbeddingBenchmark(id))
                        .as(id)
                        .isTrue());
    }

    private static List<String> concat(List<String>... groups) {
        return Arrays.stream(groups).flatMap(List::stream).toList();
    }
}
