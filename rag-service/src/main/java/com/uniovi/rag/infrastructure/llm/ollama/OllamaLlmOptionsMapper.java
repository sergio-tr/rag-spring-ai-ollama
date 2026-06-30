package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatRequest;
import java.util.List;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaOptions;

/** Maps {@link LlmChatRequest} sampling fields into Spring AI {@link OllamaOptions} for Ollama {@code /api/chat}. */
public final class OllamaLlmOptionsMapper {

    private OllamaLlmOptionsMapper() {}

    public static OllamaOptions toOllamaOptions(LlmChatRequest request) {
        var builder = OllamaOptions.builder().model(request.model().trim());
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        applyAdditionalParameters(builder, request.additionalParameters());
        return builder.build();
    }

    private static void applyAdditionalParameters(OllamaOptions.Builder builder, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        applyInteger(builder::topK, parameters, "topK", "top_k");
        applyDouble(builder::topP, parameters, "topP", "top_p");
        applyInteger(builder::numPredict, parameters, "numPredict", "num_predict", "maxTokens", "max_tokens");
        applyDouble(builder::repeatPenalty, parameters, "repeatPenalty", "repeat_penalty");
        applyInteger(builder::numCtx, parameters, "numCtx", "num_ctx");
        applyInteger(builder::seed, parameters, "seed");
        applyBoolean(builder::internalToolExecutionEnabled, parameters, "internalToolExecutionEnabled");
        if (parameters.containsKey("stop") && parameters.get("stop") instanceof List<?> stopList) {
            @SuppressWarnings("unchecked")
            List<String> stops = (List<String>) stopList;
            if (!stops.isEmpty()) {
                builder.stop(stops);
            }
        }
        if (parameters.containsKey("format") && parameters.get("format") instanceof String format && !format.isBlank()) {
            builder.format(format);
        }
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(Integer value);
    }

    @FunctionalInterface
    private interface DoubleConsumer {
        void accept(Double value);
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(Boolean value);
    }

    private static void applyInteger(IntConsumer consumer, Map<String, Object> parameters, String... keys) {
        Integer value = readInteger(parameters, keys);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private static void applyDouble(DoubleConsumer consumer, Map<String, Object> parameters, String... keys) {
        Double value = readDouble(parameters, keys);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private static void applyBoolean(BooleanConsumer consumer, Map<String, Object> parameters, String... keys) {
        Boolean value = readBoolean(parameters, keys);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private static Integer readInteger(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Double readDouble(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static Boolean readBoolean(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                continue;
            }
            Object raw = parameters.get(key);
            if (raw instanceof Boolean bool) {
                return bool;
            }
        }
        return null;
    }
}
