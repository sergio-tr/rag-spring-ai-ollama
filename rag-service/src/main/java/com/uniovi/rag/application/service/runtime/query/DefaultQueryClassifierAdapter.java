package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.query.pipeline.ClassifierOverrides;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DefaultQueryClassifierAdapter implements QueryClassifierAdapter {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";
    public static final String DEFAULT_MODEL_ID = "default";

    private final QueryClassifier classifier;

    public DefaultQueryClassifierAdapter(QueryClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public ClassifierOutcome classify(ExecutionContext ctx, String normalizedText) {
        RagConfig rag = ctx.resolved().toRagConfig();
        String modelIdUsed = resolveClassifierModelIdUsed(rag);
        if (!rag.toolsEnabled()) {
            return new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.DISABLED, modelIdUsed, "DISABLED");
        }
        try {
            QueryType out = classifier.classify(normalizedText);
            out = ClassifierOverrides.apply(normalizedText, out);
            if (out == null) {
                return new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.INVALID_OUTPUT, modelIdUsed, "INVALID_OUTPUT");
            }
            return new ClassifierOutcome(out.name(), Optional.of(out), ClassifierStatus.OK, modelIdUsed, "OK");
        } catch (Exception e) {
            return new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.UNAVAILABLE, modelIdUsed,
                    "UNAVAILABLE: " + safeMsg(e));
        }
    }

    private static String resolveClassifierModelIdUsed(RagConfig rag) {
        if (rag == null) {
            return DEFAULT_MODEL_ID;
        }
        String v = rag.classifierModelId();
        return v == null || v.isBlank() ? DEFAULT_MODEL_ID : v.trim();
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}

