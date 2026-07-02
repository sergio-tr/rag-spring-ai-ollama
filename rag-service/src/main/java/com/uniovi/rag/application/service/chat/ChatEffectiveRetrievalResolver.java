package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.result.chat.EffectiveRetrievalParameters;
import com.uniovi.rag.domain.config.RetrievalParameterKeys;
import com.uniovi.rag.domain.config.RetrievalParameterPolicy;
import com.uniovi.rag.domain.config.RetrievalParameterPolicySupport;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves effective chat retrieval values and their provenance for UI display. */
@Component
public class ChatEffectiveRetrievalResolver {

    public EffectiveRetrievalParameters resolve(
            Map<String, Object> effectiveConfig,
            Map<String, Object> runtimeOverride,
            Map<String, Object> presetValues) {
        int topK = readInt(effectiveConfig, RetrievalParameterKeys.TOP_K, 8);
        double threshold =
                readDouble(effectiveConfig, RetrievalParameterKeys.SIMILARITY_THRESHOLD, 0.25);
        RetrievalParameterPolicy topKSource =
                RetrievalParameterPolicySupport.sourceForKey(
                        RetrievalParameterKeys.TOP_K, runtimeOverride, presetValues);
        RetrievalParameterPolicy thresholdSource =
                RetrievalParameterPolicySupport.sourceForKey(
                        RetrievalParameterKeys.SIMILARITY_THRESHOLD, runtimeOverride, presetValues);
        return new EffectiveRetrievalParameters(
                topK, threshold, topKSource.name(), thresholdSource.name());
    }

    private static int readInt(Map<String, Object> config, String key, int fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static double readDouble(Map<String, Object> config, String key, double fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }
}
