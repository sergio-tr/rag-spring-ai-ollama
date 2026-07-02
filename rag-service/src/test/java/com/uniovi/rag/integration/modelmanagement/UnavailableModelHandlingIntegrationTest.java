package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.exception.llm.LlmRemoteFailures;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.model.ModelGovernanceService;
import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.llm.catalog.MeSelectableLlmModelsService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelDto;
import com.uniovi.rag.testsupport.llm.LlmCatalogApiServiceTestSupport;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.document.Document;
import org.springframework.web.server.ResponseStatusException;

/** Phase 6 — configured models stay visible; unavailable/incompatible models block with clear codes. */
@ExtendWith(MockitoExtension.class)
class UnavailableModelHandlingIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private OllamaModelAvailabilityPort ollamaAvailability;
    @Mock private ModelCatalogPort modelCatalogPort;
    @Mock private ProviderAwareEmbeddingModelFactory embeddingModelFactory;

    private LlmCatalogApiService catalogApiService;
    private MeSelectableLlmModelsService selectableService;
    private EvaluationModelCatalogService evaluationCatalogService;

    @BeforeEach
    void setUp() {
        lenient().when(modelCatalogPort.blockedLlmNamesInGovernance()).thenReturn(Set.of());
        lenient().when(modelCatalogPort.blockedEmbeddingNamesInGovernance()).thenReturn(Set.of());
        LlmProperties properties = catalogProperties();
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        ModelGovernanceService governanceService = new ModelGovernanceService(modelCatalogPort, catalog);
        catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        catalog, ollamaAvailability, new RagVectorProperties(1024, true), modelCatalogPort, true);
        selectableService = new MeSelectableLlmModelsService(configResolver, catalogApiService, governanceService);
        evaluationCatalogService = new EvaluationModelCatalogService(configResolver, catalogApiService);
    }

    @Test
    void configuredButUnavailableModelIsReportedNotHidden() {
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(false);

        LlmCatalogResponseDto response =
                catalogApiService.listCatalog(LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT, null, true);

        assertThat(response.models()).extracting(LlmCatalogModelDto::modelName).contains("ollama-chat-a");
        LlmCatalogModelDto row =
                response.models().stream()
                        .filter(m -> "ollama-chat-a".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(row.available()).isTrue();
        assertThat(row.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.UNAVAILABLE);
        assertThat(row.runtimeDetail()).contains("not installed");
    }

    @Test
    void runtimeRejectedModelReturnsClearError() {
        var rejected =
                LlmRemoteFailures.invalidModel(
                        LlmProvider.OPENAI_COMPATIBLE, "chat", "rejected-model", "http://litellm:4000", "HTTP 400");

        assertThat(rejected.publicMessage())
                .startsWith(LlmModelReasonCodes.LLM_MODEL_UNAVAILABLE + ":");
        assertThat(rejected.publicMessage()).contains("rejected-model");
    }

    @Test
    void selectedUnavailableUserModelBlocksExecutionWithClearError() {
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(false);
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "ollama-chat-a",
                                "mxbai-embed-large:latest",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));

        MeSelectableLlmModelDto row =
                selectableService.listForUser(USER_ID, LlmModelCapability.CHAT).models().stream()
                        .filter(m -> "ollama-chat-a".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();

        assertThat(row.selectable()).isFalse();
        assertThat(row.disabledReasonCode()).isEqualTo(LlmModelReasonCodes.LLM_MODEL_UNAVAILABLE);
        assertThat(row.disabledReason()).contains("not installed");

        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(catalogProperties());
        assertThatThrownBy(
                        () ->
                                catalog.assertUsable(
                                        LlmProvider.OLLAMA_NATIVE,
                                        "unknown-chat",
                                        LlmModelCapability.CHAT,
                                        LlmModelUsageContext.USER_SELECTION))
                .isInstanceOf(LlmConfigurationException.class)
                .satisfies(
                        ex ->
                                assertThat(((LlmConfigurationException) ex).publicMessage())
                                        .startsWith(LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED + ":"));
    }

    @Test
    void incompatibleEmbeddingModelBlocksIngestionWithClearError() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.getOllama().setAvailableEmbeddingModels(List.of("nomic-embed-text"));
        properties.getOllama().setDefaultEmbeddingModel("nomic-embed-text");
        when(configResolver.resolve(any(), isNull(), isNull()))
                .thenReturn(
                        new ResolvedLlmConfig(
                                LlmProvider.OPENAI_COMPATIBLE,
                                LlmProvider.OLLAMA_NATIVE,
                                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                                properties.getOpenAiCompatible().getDefaultChatModel(),
                                "nomic-embed-text",
                                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        EvaluationModelCatalogService isolatedEval =
                new EvaluationModelCatalogService(
                        configResolver,
                        LlmCatalogApiServiceTestSupport.service(
                                catalog, ollamaAvailability, new RagVectorProperties(1024, true)));

        assertThatThrownBy(
                        () ->
                                isolatedEval.assertEmbeddingCompatibleWithVectorStore(
                                        USER_ID, ProductDemoModel.NOMIC_EMBED_TEXT.modelId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(
                                                LlmModelReasonCodes
                                                        .EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE));

        when(embeddingModelFactory.effectiveModelId("nomic-embed-text")).thenReturn("nomic-embed-text");
        when(embeddingModelFactory.forModel("nomic-embed-text")).thenReturn(new FixedWidthEmbeddingModel(768));
        EmbeddingSpaceGuard guard =
                new EmbeddingSpaceGuard(embeddingModelFactory, new RagVectorProperties(1024, true));
        assertThatThrownBy(() -> guard.assertFitsPhysicalVectorColumnReturning("nomic-embed-text"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .startsWith(LlmModelReasonCodes.EMBEDDING_DIMENSION_MISMATCH + ":"));
    }

    private static LlmProperties catalogProperties() {
        LlmProperties properties = new LlmProperties();
        var ollama = properties.getOllama();
        ollama.setAvailableChatModels(List.of("ollama-chat-a", "ollama-chat-b"));
        ollama.setDefaultChatModel("ollama-chat-a");
        ollama.setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        var openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        openAi.setDefaultEmbeddingModel("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        openAi.setAvailableEmbeddingModels(List.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        return properties;
    }

    private static ResolvedLlmConfig openAiConfig(LlmProperties properties) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                properties.getOpenAiCompatible().getDefaultChatModel(),
                properties.getOpenAiCompatible().getDefaultEmbeddingModel(),
                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                null,
                null,
                null,
                null,
                Map.of());
    }

    private static final class FixedWidthEmbeddingModel implements EmbeddingModel {
        private final float[] vector;

        FixedWidthEmbeddingModel(int dimensions) {
            vector = new float[dimensions];
            Arrays.fill(vector, 0.01f);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(Arrays.copyOf(vector, vector.length), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return Arrays.copyOf(vector, vector.length);
        }

        @Override
        public float[] embed(String text) {
            return Arrays.copyOf(vector, vector.length);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            List<float[]> out = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                out.add(Arrays.copyOf(vector, vector.length));
            }
            return out;
        }
    }
}
