package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolApplicability;
import com.uniovi.rag.configuration.RagClassifierProperties;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.classifier.ClassifierCallException;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceResponse;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import com.uniovi.rag.infrastructure.observability.TelemetryRedaction;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DefaultQueryClassifierAdapter implements QueryClassifierAdapter {

    private static final Logger log = LoggerFactory.getLogger(DefaultQueryClassifierAdapter.class);

    public static final String UNCLASSIFIED = "UNCLASSIFIED";
    public static final String DEFAULT_MODEL_ID = "default";

    private final QueryClassifier classifier;
    private final RagClassifierProperties classifierProperties;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;
    private final Tracer tracer;

    public DefaultQueryClassifierAdapter(
            QueryClassifier classifier,
            RagClassifierProperties classifierProperties,
            ObjectProvider<RuntimeObservability> runtimeObservability,
            ObjectProvider<Tracer> tracer) {
        this.classifier = classifier;
        this.classifierProperties = classifierProperties;
        this.runtimeObservability = runtimeObservability;
        this.tracer = tracer.getIfAvailable();
    }

    @Override
    public ClassifierOutcome classify(ExecutionContext ctx, String normalizedText) {
        RagConfig rag = ctx.resolved().toRagConfig();
        String modelIdUsed = resolveClassifierModelIdUsed(rag);
        if (!rag.toolsEnabled()) {
            ClassifierOutcome disabled =
                    new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.DISABLED, modelIdUsed, "DISABLED");
            tagClassifierSpan(disabled);
            return disabled;
        }
        try {
            ClassifierInferenceResponse inference = classifier.classifyInference(normalizedText, modelIdUsed);
            if (inference == null || inference.queryType() == null || inference.queryType().isBlank()) {
                return recoverOrInvalid(ctx, modelIdUsed, normalizedText, "INVALID_OUTPUT");
            }
            QueryType parsed = parseQueryType(inference.queryType());
            if (parsed == null) {
                return recoverOrInvalid(ctx, modelIdUsed, normalizedText, "INVALID_OUTPUT:unknown_label");
            }
            Optional<QueryType> deterministic = ClassifierDeterministicResolver.resolve(normalizedText);
            if (deterministic.isPresent()) {
                return deterministicOutcome(
                        deterministic.get(), modelIdUsed, "DETERMINISTIC_PATTERN", inference);
            }
            Optional<QueryType> explicitRule = ClassifierOverrides.matchRule(normalizedText);
            if (explicitRule.isPresent()) {
                return ruleOutcome(explicitRule.get(), modelIdUsed, "RULE_OVERRIDE", inference);
            }
            QueryType overridden = ClassifierOverrides.apply(normalizedText, parsed);
            if (ClassifierOverrides.shouldRejectCountDocumentsForUndatedParticipantCount(normalizedText, overridden)) {
                ClassifierOutcome low =
                        new ClassifierOutcome(
                                UNCLASSIFIED,
                                Optional.empty(),
                                ClassifierStatus.LOW_CONFIDENCE,
                                modelIdUsed,
                                "UNDATED_PARTICIPANT_COUNT_REQUIRES_CLARIFICATION",
                                Optional.ofNullable(inference.confidence()),
                                optionalHash(inference));
                tagClassifierSpan(low);
                return low;
            }
            if (overridden != parsed) {
                return ruleOutcome(overridden, modelIdUsed, "RULE_OVERRIDE", inference);
            }
            if (isLowConfidence(inference.confidence())) {
                if (DeterministicToolApplicability.isApplicableQueryType(parsed)) {
                    return ruleOutcome(parsed, modelIdUsed, "APPLICABLE_LOW_CONFIDENCE", inference);
                }
                ClassifierOutcome low =
                        new ClassifierOutcome(
                                UNCLASSIFIED,
                                Optional.empty(),
                                ClassifierStatus.LOW_CONFIDENCE,
                                modelIdUsed,
                                "LOW_CONFIDENCE",
                                Optional.ofNullable(inference.confidence()),
                                optionalHash(inference));
                tagClassifierSpan(low);
                return low;
            }
            ClassifierOutcome ok =
                    new ClassifierOutcome(
                            overridden.name(),
                            Optional.of(overridden),
                            ClassifierStatus.OK,
                            modelIdUsed,
                            "OK",
                            Optional.ofNullable(inference.confidence()),
                            optionalHash(inference));
            tagClassifierSpan(ok);
            return ok;
        } catch (ClassifierCallException e) {
            ClassifierOutcome recoverable = mapClassifierFailure(ctx, modelIdUsed, e);
            Optional<QueryType> deterministic = ClassifierDeterministicResolver.resolve(normalizedText);
            if (deterministic.isPresent() && recoverable.classifierStatus() != ClassifierStatus.OK) {
                ClassifierOutcome recovered =
                        new ClassifierOutcome(
                                deterministic.get().name(),
                                deterministic,
                                ClassifierStatus.OK,
                                modelIdUsed,
                                "DETERMINISTIC_RECOVERED_" + recoverable.classifierStatus().name());
                tagClassifierSpan(recovered);
                return recovered;
            }
            tagClassifierSpan(recoverable);
            return recoverable;
        } catch (Exception e) {
            log.debug(
                    "query_classifier_recoverable correlationId={} status=UNAVAILABLE modelId={} detail={}",
                    ctx.correlationId(),
                    modelIdUsed,
                    safeMsg(e));
            ClassifierOutcome unavailable =
                    new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.UNAVAILABLE, modelIdUsed,
                            "UNAVAILABLE: " + safeMsg(e));
            tagClassifierSpan(unavailable);
            return unavailable;
        }
    }

    private ClassifierOutcome recoverOrInvalid(
            ExecutionContext ctx, String modelIdUsed, String normalizedText, String reason) {
        Optional<QueryType> deterministic = ClassifierDeterministicResolver.resolve(normalizedText);
        if (deterministic.isPresent()) {
            ClassifierOutcome recovered =
                    new ClassifierOutcome(
                            deterministic.get().name(),
                            deterministic,
                            ClassifierStatus.OK,
                            modelIdUsed,
                            "DETERMINISTIC_RECOVERED_" + reason);
            tagClassifierSpan(recovered);
            return recovered;
        }
        return invalidOutput(ctx, modelIdUsed, reason);
    }

    private ClassifierOutcome ruleOutcome(
            QueryType type, String modelIdUsed, String note, ClassifierInferenceResponse inference) {
        ClassifierOutcome outcome =
                new ClassifierOutcome(
                        type.name(),
                        Optional.of(type),
                        ClassifierStatus.OK,
                        modelIdUsed,
                        note,
                        Optional.ofNullable(inference.confidence()),
                        optionalHash(inference));
        tagClassifierSpan(outcome);
        return outcome;
    }

    private ClassifierOutcome deterministicOutcome(
            QueryType type, String modelIdUsed, String note, ClassifierInferenceResponse inference) {
        return ruleOutcome(type, modelIdUsed, note, inference);
    }

    private ClassifierOutcome invalidOutput(ExecutionContext ctx, String modelIdUsed, String reason) {
        log.debug(
                "query_classifier_recoverable correlationId={} status=INVALID_OUTPUT modelId={}",
                ctx.correlationId(),
                modelIdUsed);
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.classifierInvalidOutput();
        }
        ClassifierOutcome invalid =
                new ClassifierOutcome(UNCLASSIFIED, Optional.empty(), ClassifierStatus.INVALID_OUTPUT, modelIdUsed, reason);
        tagClassifierSpan(invalid);
        return invalid;
    }

    private boolean isLowConfidence(Double confidence) {
        return confidence != null && confidence < classifierProperties.getConfidenceThreshold();
    }

    private static QueryType parseQueryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return QueryType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Optional<String> optionalHash(ClassifierInferenceResponse inference) {
        String hash = inference.labelSetHash();
        return hash == null || hash.isBlank() ? Optional.empty() : Optional.of(hash.trim());
    }

    private void tagClassifierSpan(ClassifierOutcome outcome) {
        if (tracer == null || outcome == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("classifierModelId", outcome.classifierModelIdUsed());
        tags.put("classifierStatus", outcome.classifierStatus().name());
        if (outcome.note() != null && !outcome.note().isBlank()) {
            tags.put("classifierFallbackReason", outcome.note());
        }
        if (outcome.classifierQueryType().isPresent()) {
            tags.put("predictedQueryType", outcome.classifierQueryType().get().name());
        }
        outcome.classifierConfidence().ifPresent(c -> tags.put("classifierConfidence", String.valueOf(c)));
        TelemetryRedaction.safeAttributes(tags).forEach(span::tag);
    }

    private static String resolveClassifierModelIdUsed(RagConfig rag) {
        if (rag == null) {
            return DEFAULT_MODEL_ID;
        }
        String v = rag.classifierModelId();
        return v == null || v.isBlank() ? DEFAULT_MODEL_ID : v.trim();
    }

    private ClassifierOutcome mapClassifierFailure(ExecutionContext ctx, String modelIdUsed, ClassifierCallException e) {
        ClassifierStatus status =
                switch (e.kind()) {
                    case INVALID_OUTPUT -> ClassifierStatus.INVALID_OUTPUT;
                    case INVALID_REQUEST -> ClassifierStatus.INVALID_REQUEST;
                    case TIMEOUT -> ClassifierStatus.TIMEOUT;
                    case UNAVAILABLE -> ClassifierStatus.UNAVAILABLE;
                };
        log.debug(
                "query_classifier_recoverable correlationId={} status={} modelId={} httpStatus={}",
                ctx.correlationId(),
                status,
                modelIdUsed,
                e.httpStatus());
        return new ClassifierOutcome(
                UNCLASSIFIED,
                Optional.empty(),
                status,
                modelIdUsed,
                status.name() + ": classifier call failed");
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
