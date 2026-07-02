package com.uniovi.rag.application.service.embedding;

import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import com.uniovi.rag.domain.embedding.IndexingRequestOptions;
import com.uniovi.rag.domain.embedding.RetrievalRequestOptions;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses and builds nested Lab embedding runtime parameter groups. */
public final class EmbeddingBenchmarkRuntimeParameters {

    private EmbeddingBenchmarkRuntimeParameters() {}

    public static EmbeddingRequestOptions readEmbeddingOptions(Map<String, Object> runtimeParameters) {
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return new EmbeddingRequestOptions(null, null, null, null);
        }
        Map<String, Object> nested = readNested(runtimeParameters, EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS);
        String encodingFormat = firstString(nested, runtimeParameters, "encodingFormat", "encoding_format");
        Integer dimensions = firstPositiveInt(nested, runtimeParameters, "dimensions");
        String user = firstString(nested, runtimeParameters, "user");
        Integer timeoutSeconds = firstPositiveInt(nested, runtimeParameters, "timeoutSeconds", "timeout_seconds");
        return new EmbeddingRequestOptions(encodingFormat, dimensions, user, timeoutSeconds);
    }

    public static RetrievalRequestOptions readRetrievalOptions(Map<String, Object> runtimeParameters) {
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return new RetrievalRequestOptions(null, null, null);
        }
        Map<String, Object> nested = readNested(runtimeParameters, EmbeddingConfigurationKeys.RUNTIME_RETRIEVAL_OPTIONS);
        Integer topK = firstPositiveInt(nested, runtimeParameters, "topK", "top_k");
        Double threshold =
                firstDouble(nested, runtimeParameters, "similarityThreshold", "similarity_threshold");
        String materialization =
                firstString(
                        nested,
                        runtimeParameters,
                        "materializationStrategy",
                        "materialization_strategy",
                        "presetGroupKey");
        return new RetrievalRequestOptions(topK, threshold, materialization);
    }

    public static IndexingRequestOptions readIndexingOptions(Map<String, Object> runtimeParameters) {
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return new IndexingRequestOptions(null, null, null, null);
        }
        Map<String, Object> nested = readNested(runtimeParameters, EmbeddingConfigurationKeys.RUNTIME_INDEXING_OPTIONS);
        Integer batchSize = firstPositiveInt(nested, runtimeParameters, "batchSize", "batch_size");
        Integer maxInputChars = firstPositiveInt(nested, runtimeParameters, "maxInputChars", "max_input_chars");
        Boolean normalize = firstBoolean(nested, runtimeParameters, "normalize");
        String truncate = firstString(nested, runtimeParameters, "truncate");
        return new IndexingRequestOptions(batchSize, maxInputChars, normalize, truncate);
    }

    public static Map<String, Object> toExportPayload(
            EmbeddingRequestOptions embedding,
            RetrievalRequestOptions retrieval,
            IndexingRequestOptions indexing) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> embeddingMap = embeddingMap(embedding);
        if (!embeddingMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS, embeddingMap);
        }
        Map<String, Object> retrievalMap = retrievalMap(retrieval);
        if (!retrievalMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_RETRIEVAL_OPTIONS, retrievalMap);
        }
        Map<String, Object> indexingMap = indexingMap(indexing);
        if (!indexingMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_INDEXING_OPTIONS, indexingMap);
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> mergeIntoRuntimeParameters(
            Map<String, Object> base,
            EmbeddingRequestOptions embedding,
            RetrievalRequestOptions retrieval,
            IndexingRequestOptions indexing) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) {
            out.putAll(base);
        }
        Map<String, Object> embeddingMap = embeddingMap(embedding);
        if (!embeddingMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS, embeddingMap);
        }
        Map<String, Object> retrievalMap = retrievalMap(retrieval);
        if (!retrievalMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_RETRIEVAL_OPTIONS, retrievalMap);
        }
        Map<String, Object> indexingMap = indexingMap(indexing);
        if (!indexingMap.isEmpty()) {
            out.put(EmbeddingConfigurationKeys.RUNTIME_INDEXING_OPTIONS, indexingMap);
        }
        if (retrieval != null && retrieval.topK() != null) {
            out.put("topK", retrieval.topK());
        }
        if (retrieval != null && retrieval.similarityThreshold() != null) {
            out.put("similarityThreshold", retrieval.similarityThreshold());
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> toLlmAdditionalParameters(EmbeddingRequestOptions options) {
        if (options == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (options.encodingFormat() != null) {
            out.put("encoding_format", options.encodingFormat());
            out.put("encodingFormat", options.encodingFormat());
        }
        if (options.dimensions() != null) {
            out.put("dimensions", options.dimensions());
        }
        if (options.user() != null) {
            out.put("user", options.user());
        }
        return Map.copyOf(out);
    }

    public static Integer resolveTimeoutMs(EmbeddingRequestOptions options) {
        if (options == null || options.timeoutSeconds() == null || options.timeoutSeconds() <= 0) {
            return null;
        }
        long ms = options.timeoutSeconds() * 1000L;
        return (int) Math.min(Integer.MAX_VALUE, ms);
    }

    private static Map<String, Object> embeddingMap(EmbeddingRequestOptions embedding) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (embedding == null) {
            return map;
        }
        if (embedding.encodingFormat() != null) {
            map.put("encodingFormat", embedding.encodingFormat());
        }
        if (embedding.dimensions() != null) {
            map.put("dimensions", embedding.dimensions());
        }
        if (embedding.user() != null) {
            map.put("user", embedding.user());
        }
        if (embedding.timeoutSeconds() != null) {
            map.put("timeoutSeconds", embedding.timeoutSeconds());
        }
        return map;
    }

    private static Map<String, Object> retrievalMap(RetrievalRequestOptions retrieval) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (retrieval == null) {
            return map;
        }
        if (retrieval.topK() != null) {
            map.put("topK", retrieval.topK());
        }
        if (retrieval.similarityThreshold() != null) {
            map.put("similarityThreshold", retrieval.similarityThreshold());
        }
        if (retrieval.materializationStrategy() != null) {
            map.put("materializationStrategy", retrieval.materializationStrategy());
        }
        return map;
    }

    private static Map<String, Object> indexingMap(IndexingRequestOptions indexing) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (indexing == null) {
            return map;
        }
        if (indexing.batchSize() != null) {
            map.put("batchSize", indexing.batchSize());
        }
        if (indexing.maxInputChars() != null) {
            map.put("maxInputChars", indexing.maxInputChars());
        }
        if (indexing.normalize() != null) {
            map.put("normalize", indexing.normalize());
        }
        if (indexing.truncate() != null) {
            map.put("truncate", indexing.truncate());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readNested(Map<String, Object> runtimeParameters, String key) {
        Object raw = runtimeParameters.get(key);
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String k && !k.isBlank() && entry.getValue() != null) {
                out.put(k.trim(), entry.getValue());
            }
        }
        return out;
    }

    private static String firstString(Map<String, Object> nested, Map<String, Object> flat, String... keys) {
        for (String key : keys) {
            Object raw = nested.get(key);
            if (raw == null) {
                raw = flat.get(key);
            }
            if (raw instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private static Integer firstPositiveInt(Map<String, Object> nested, Map<String, Object> flat, String... keys) {
        for (String key : keys) {
            Object raw = nested.get(key);
            if (raw == null) {
                raw = flat.get(key);
            }
            if (raw instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Double firstDouble(Map<String, Object> nested, Map<String, Object> flat, String... keys) {
        for (String key : keys) {
            Object raw = nested.get(key);
            if (raw == null) {
                raw = flat.get(key);
            }
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static Boolean firstBoolean(Map<String, Object> nested, Map<String, Object> flat, String... keys) {
        for (String key : keys) {
            Object raw = nested.get(key);
            if (raw == null) {
                raw = flat.get(key);
            }
            if (raw instanceof Boolean bool) {
                return bool;
            }
        }
        return null;
    }
}
