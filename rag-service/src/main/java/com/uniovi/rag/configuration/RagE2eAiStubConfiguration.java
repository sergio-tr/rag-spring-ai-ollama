package com.uniovi.rag.configuration;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Spring AI beans for profile {@code e2e}: Playwright fullstack and CI without Ollama.
 * Replaces provider {@link EmbeddingModel}, {@link ChatModel}, per-snapshot embedding factories, and Lab classifier
 * HTTP calls via {@link Primary}.
 */
@Configuration
@Profile("e2e")
public class RagE2eAiStubConfiguration {

    static final int E2E_EMBEDDING_DIMENSIONS = 1024;
    static final String E2E_EMBEDDING_MODEL_ID = "mxbai-embed-large:latest";
    private static final String MODEL_ID_KEY = "modelId";
    private static final String ACCURACY_KEY = "accuracy";
    private static final String DEFAULT_CLASSIFIER_MODEL_ID = "default";

    private static final ResolvedLlmConfig E2E_LLM_CONFIG =
            ResolvedLlmConfig.uniform(
                    LlmProvider.OLLAMA_NATIVE,
                    "http://127.0.0.1:1",
                    "e2e-stub-chat",
                    E2E_EMBEDDING_MODEL_ID,
                    null,
                    null,
                    0.1,
                    60_000,
                    null,
                    Map.of());

    @Bean
    @Primary
    public OllamaApiClient e2eOllamaApiClient(RagHealthProperties healthProperties) {
        return OllamaApiClient.noHttpStub(healthProperties);
    }

    /** Avoid per-model Ollama HTTP probes when building the selectable CHAT catalog in CI. */
    @Bean
    @Primary
    public OllamaModelAvailabilityPort e2eOllamaModelAvailabilityPort() {
        return modelName -> modelName != null && !modelName.isBlank();
    }

    @Bean
    @Primary
    public EmbeddingModel e2eEmbeddingModel() {
        return new E2eStubEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatModel e2eChatModel() {
        return new E2eStubChatModel();
    }

    @Bean
    @Primary
    public ProviderAwareEmbeddingModelFactory e2eProviderAwareEmbeddingModelFactory() {
        return new E2eProviderAwareEmbeddingModelFactory();
    }

    /**
     * In-memory embeddings + fixed {@link ResolvedLlmConfig} for knowledge ingest compatibility checks
     * ({@link com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService}) without LiteLLM/Ollama HTTP.
     */
    @Bean
    @Primary
    public ProviderAwareEmbeddingService e2eProviderAwareEmbeddingService(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            EmbeddingModelCatalogResolver embeddingModelCatalogResolver) {
        return new ProviderAwareEmbeddingService(
                llmClientResolver, resolvedLlmConfigResolver, embeddingModelCatalogResolver) {
            @Override
            public ResolvedLlmConfig resolveEffectiveConfig() {
                return OrchestrationLlmConfigScope.current().orElse(E2E_LLM_CONFIG);
            }

            @Override
            public String effectiveEmbeddingModelId(String requestedModelId) {
                // Canonical model for index-profile enrichment + compatibility checks in CI (no LiteLLM/Ollama HTTP).
                return E2E_EMBEDDING_MODEL_ID;
            }

            @Override
            public LlmEmbeddingResponse embed(String modelId, List<String> texts) {
                if (texts == null || texts.isEmpty()) {
                    throw new IllegalArgumentException("texts must not be empty");
                }
                String effectiveModelId = effectiveEmbeddingModelId(modelId);
                List<float[]> vectors = new ArrayList<>(texts.size());
                for (int i = 0; i < texts.size(); i++) {
                    vectors.add(E2eStubEmbeddingModel.copyVector());
                }
                return new LlmEmbeddingResponse(effectiveModelId, vectors, Map.of());
            }
        };
    }

    @Bean
    @Primary
    public OllamaEmbeddingModelFactory e2eOllamaEmbeddingModelFactory() {
        return new E2eOllamaEmbeddingModelFactory();
    }

    @Bean
    @Primary
    public ClassifierLabPort e2eClassifierLabPort() {
        return new E2eClassifierLabPort();
    }

    static final class E2eStubEmbeddingModel implements EmbeddingModel {

        private final float[] vector;

        E2eStubEmbeddingModel() {
            vector = copyVector();
        }

        private static float[] copyVector() {
            float[] out = new float[E2E_EMBEDDING_DIMENSIONS];
            Arrays.fill(out, 0.01f);
            return out;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(copyVector(), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return copyVector();
        }

        @Override
        public float[] embed(String text) {
            return copyVector();
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            List<float[]> out = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                out.add(copyVector());
            }
            return out;
        }

        @Override
        public int dimensions() {
            return E2E_EMBEDDING_DIMENSIONS;
        }
    }

    static final class E2eStubChatModel implements ChatModel {

        static final String E2E_REPLY = "E2E stub reply: RAG pipeline ran without a live Ollama server.";

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(E2E_REPLY))));
        }
    }

    static final class E2eProviderAwareEmbeddingModelFactory extends ProviderAwareEmbeddingModelFactory {

        E2eProviderAwareEmbeddingModelFactory() {
            super(null);
        }

        @Override
        public String effectiveModelId(String requestedModelId) {
            return E2E_EMBEDDING_MODEL_ID;
        }

        @Override
        public EmbeddingModel forModel(String modelId) {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException(MODEL_ID_KEY);
            }
            return new E2eStubEmbeddingModel();
        }
    }

    static final class E2eOllamaEmbeddingModelFactory extends OllamaEmbeddingModelFactory {

        E2eOllamaEmbeddingModelFactory() {
            super(new OllamaApi("http://127.0.0.1:1"), ObservationRegistry.NOOP);
        }

        @Override
        public EmbeddingModel forModel(String modelId) {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException(MODEL_ID_KEY);
            }
            return new E2eStubEmbeddingModel();
        }
    }

    static final class E2eClassifierLabPort implements ClassifierLabPort {

        @Override
        public Map<String, Object> train(
                MultipartFile file,
                String modelName,
                String labelsJson,
                MultipartFile labelsFile,
                int epochs,
                int batchSize)
                throws IOException {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Training file is required");
            }
            return trainBytes(new ClassifierTrainBytesCommand(
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "dataset.xlsx",
                    modelName,
                    labelsJson,
                    labelsFile != null && !labelsFile.isEmpty() ? labelsFile.getBytes() : null,
                    labelsFile != null ? labelsFile.getOriginalFilename() : null,
                    epochs,
                    batchSize));
        }

        @Override
        public Map<String, Object> trainBytes(ClassifierTrainBytesCommand command) {
            String modelName =
                    command.modelName() != null && !command.modelName().isBlank()
                            ? command.modelName().trim()
                            : "e2e-classifier";
            String modelId = "e2e/" + modelName;
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put(ACCURACY_KEY, 1.0);
            metrics.put("f1Macro", 1.0);
            metrics.put("samples", 4);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put(MODEL_ID_KEY, modelId);
            out.put("name", modelName);
            out.put("metrics", metrics);
            return out;
        }

        @Override
        public Map<String, Object> evaluate(String modelId, boolean includeImages, MultipartFile datasetFile)
                throws IOException {
            byte[] bytes = datasetFile != null && !datasetFile.isEmpty() ? datasetFile.getBytes() : null;
            String filename =
                    datasetFile != null && datasetFile.getOriginalFilename() != null
                            ? datasetFile.getOriginalFilename()
                            : "eval.xlsx";
            return evaluateBytes(modelId, includeImages, bytes, filename);
        }

        @Override
        public Map<String, Object> evaluateBytes(
                String modelId, boolean includeImages, byte[] datasetContent, String datasetFilename) {
            Map<String, Object> classificationReport = new LinkedHashMap<>();
            classificationReport.put(ACCURACY_KEY, 1.0);
            classificationReport.put("macroF1", 1.0);

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put(ACCURACY_KEY, 1.0);
            metrics.put("classificationReport", classificationReport);
            metrics.put("confusionMatrix", List.of(List.of(2, 0), List.of(0, 2)));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put(MODEL_ID_KEY, modelId != null && !modelId.isBlank() ? modelId : DEFAULT_CLASSIFIER_MODEL_ID);
            out.put("metrics", metrics);
            return out;
        }

        @Override
        public Map<String, Object> classify(String query, String modelId) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("query", query != null ? query : "");
            out.put(MODEL_ID_KEY, modelId != null && !modelId.isBlank() ? modelId : DEFAULT_CLASSIFIER_MODEL_ID);
            out.put("queryType", "COUNT_DOCUMENTS");
            out.put("confidence", 1.0);
            return out;
        }

        @Override
        public List<Map<String, Object>> listModels() {
            return List.of(Map.of("id", DEFAULT_CLASSIFIER_MODEL_ID, "name", "E2E default classifier"));
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}
