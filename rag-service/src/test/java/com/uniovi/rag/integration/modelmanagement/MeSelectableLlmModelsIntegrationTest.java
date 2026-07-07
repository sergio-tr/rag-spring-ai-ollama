package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.model.ModelGovernanceService;
import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.testsupport.llm.LlmCatalogApiServiceTestSupport;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.llm.catalog.MeSelectableLlmModelsService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelDto;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelsResponseDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Phase 2 - user-scoped selectable chat models from properties catalog. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeSelectableLlmModelsIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private OllamaModelAvailabilityPort ollamaAvailability;
    @Mock private ModelCatalogPort modelCatalogPort;

    private MeSelectableLlmModelsService service;

    @BeforeEach
    void setUp() {
        when(modelCatalogPort.blockedLlmNamesInGovernance()).thenReturn(Set.of());
        when(modelCatalogPort.blockedEmbeddingNamesInGovernance()).thenReturn(Set.of());
        LlmProperties properties = catalogProperties();
        LlmModelCatalogService catalogService = new LlmModelCatalogService(properties);
        ModelGovernanceService governanceService = new ModelGovernanceService(modelCatalogPort, catalogService);
        LlmCatalogApiService catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        catalogService,
                        ollamaAvailability,
                        new RagVectorProperties(1024, true),
                        modelCatalogPort,
                        true);
        service = new MeSelectableLlmModelsService(configResolver, catalogApiService, governanceService);
    }

    @Test
    void selectableModelsRespectGovernanceBlocklist() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(openAiConfig());
        when(modelCatalogPort.blockedLlmNamesInGovernance()).thenReturn(Set.of("openai-chat-b"));

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(
                        response.models().stream()
                                .filter(m -> "openai-chat-b".equals(m.modelName()))
                                .findFirst())
                .isEmpty();
        assertThat(
                        response.models().stream()
                                .filter(m -> "gpt-oss:20b".equals(m.modelName()))
                                .findFirst())
                .isPresent();
    }

    @Test
    void deepseekAcceptedWhenInPropertiesCatalogWithoutDbRow() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(openAiConfig());

        LlmProperties properties = catalogProperties();
        properties.getOpenAiCompatible().setAvailableChatModels(List.of("gpt-oss:20b", "openai-chat-b", "deepseek-v2:16b"));
        LlmModelCatalogService catalogService = new LlmModelCatalogService(properties);
        ModelGovernanceService governanceService = new ModelGovernanceService(modelCatalogPort, catalogService);
        LlmCatalogApiService catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        catalogService,
                        ollamaAvailability,
                        new RagVectorProperties(1024, true),
                        modelCatalogPort,
                        true);
        MeSelectableLlmModelsService localService =
                new MeSelectableLlmModelsService(configResolver, catalogApiService, governanceService);

        MeSelectableLlmModelsResponseDto response =
                localService.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .contains("deepseek-v2:16b");
    }

    @Test
    void selectableModelsForOpenAiCompatibleDoNotDependOnOllamaTags() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(openAiConfig());

        service.listForUser(USER_ID, LlmModelCapability.CHAT);

        verify(ollamaAvailability, never()).isModelPresent(any());
    }

    @Test
    void selectableModelsForOpenAiCompatibleIncludeConfiguredRuntimeModel() {
        ResolvedLlmConfig config = openAiConfig();
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(config);

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .contains(config.chatModel());
        MeSelectableLlmModelDto runtimeModel =
                response.models().stream()
                        .filter(m -> config.chatModel().equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(runtimeModel.usableAsDefault()).isTrue();
        assertThat(runtimeModel.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.NOT_PROBED);
        assertThat(runtimeModel.selectable()).isTrue();
    }

    @Test
    void selectableModelsForOllamaNativeCanUseOllamaRuntimeStatus() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(ollamaConfig());
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(true);
        when(ollamaAvailability.isModelPresent("ollama-chat-b")).thenReturn(false);

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        verify(ollamaAvailability).isModelPresent("ollama-chat-a");
        verify(ollamaAvailability).isModelPresent("ollama-chat-b");

        MeSelectableLlmModelDto installed =
                response.models().stream()
                        .filter(m -> "ollama-chat-a".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(installed.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.AVAILABLE);
        assertThat(installed.selectable()).isTrue();

        MeSelectableLlmModelDto missing =
                response.models().stream()
                        .filter(m -> "ollama-chat-b".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(missing.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.UNAVAILABLE);
        assertThat(missing.selectable()).isFalse();
        assertThat(missing.disabledReasonCode()).isEqualTo("LLM_MODEL_UNAVAILABLE");
        assertThat(missing.disabledReason()).contains("not installed");
    }

    @Test
    void selectableModelsForOpenAiUserReturnOnlyOpenAiChatModels() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(openAiConfig());

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.effectiveProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .containsExactly("gpt-oss:20b", "openai-chat-b");
        assertThat(response.models()).allMatch(m -> m.selectable());
        assertThat(response.models()).noneMatch(m -> m.modelName().contains("ollama"));
    }

    @Test
    void selectableModelsForOllamaUserReturnOnlyOllamaChatModels() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(ollamaConfig());
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(true);
        when(ollamaAvailability.isModelPresent("ollama-chat-b")).thenReturn(true);

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.effectiveProvider()).isEqualTo(LlmProvider.OLLAMA_NATIVE);
        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .containsExactly("ollama-chat-a", "ollama-chat-b");
        assertThat(response.models()).allMatch(MeSelectableLlmModelDto::selectable);
    }

    @Test
    void selectableModelsDoNotReturnEmbeddings() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(openAiConfig());

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.models())
                .noneMatch(m -> m.modelName().contains("mxbai") || m.modelName().contains("embed"));
    }

    @Test
    void selectableModelsDoNotReturnHardcodedLegacyModels() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(openAiConfig());

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .doesNotContain(
                        ProductDemoModel.MISTRAL_7B.modelId(),
                        ProductDemoModel.GEMMA3_4B.modelId(),
                        ProductDemoModel.LLAMA3_1_8B.modelId());
    }

    @Test
    void unavailableConfiguredModelIsReturnedWithDisabledReason() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull())).thenReturn(ollamaConfig());
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(true);
        when(ollamaAvailability.isModelPresent("ollama-chat-b")).thenReturn(false);

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.CHAT);

        MeSelectableLlmModelDto missing =
                response.models().stream()
                        .filter(m -> "ollama-chat-b".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(missing.selectable()).isFalse();
        assertThat(missing.disabledReasonCode()).isEqualTo("LLM_MODEL_UNAVAILABLE");
        assertThat(missing.disabledReason()).contains("not installed");
        assertThat(missing.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.UNAVAILABLE);

        MeSelectableLlmModelDto available =
                response.models().stream()
                        .filter(m -> "ollama-chat-a".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(available.selectable()).isTrue();
        assertThat(available.disabledReason()).isNull();
    }

    @Test
    void selectableEmbeddingModelsForOpenAiCompatibleDoNotDependOnOllamaTags() {
        when(configResolver.resolve(eq(USER_ID), isNull(), isNull()))
                .thenReturn(openAiConfig());

        MeSelectableLlmModelsResponseDto response =
                service.listForUser(USER_ID, LlmModelCapability.EMBEDDING);

        verify(ollamaAvailability, never()).isModelPresent(any());
        assertThat(response.capability()).isEqualTo(LlmModelCapability.EMBEDDING);
        assertThat(response.models())
                .extracting(MeSelectableLlmModelDto::modelName)
                .contains("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        assertThat(response.models()).allMatch(MeSelectableLlmModelDto::selectable);
    }

    private static LlmProperties catalogProperties() {
        LlmProperties props = new LlmProperties();
        LlmOllamaDefaults ollama = props.getOllama();
        ollama.setDefaultChatModel("ollama-chat-a");
        ollama.setAvailableChatModels(List.of("ollama-chat-a", "ollama-chat-b"));
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        ollama.setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        LlmOpenAiCompatibleDefaults openAi = props.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b", "openai-chat-b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        openAi.setDefaultEmbeddingModel("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        openAi.setAvailableEmbeddingModels(List.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        return props;
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ResolvedLlmConfig ollamaConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                "ollama-chat-a",
                "mxbai-embed-large:latest",
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
