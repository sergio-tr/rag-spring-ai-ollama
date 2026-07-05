package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.clarification.ClarifiedPlanningInputResolver;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.QueryExpansionResult;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultQueryUnderstandingPipeline implements QueryUnderstandingPipeline {

    private static final String QU_NOTE_DISABLED = "DISABLED";
    private static final String QU_NOTE_FALLBACK = "FALLBACK";
    private static final String QU_NOTE_ERROR = "ERROR";

    private final QueryClassifierAdapter classifierAdapter;
    private final NamedEntityExtractionAdapter entityExtractionAdapter;
    private final StructuredQueryRewriter rewriter;
    private final QueryIntentResolver intentResolver;
    private final ExpectedAnswerShapeResolver answerShapeResolver;
    private final AmbiguityAssessmentService ambiguityAssessmentService;
    private final QueryExpansionStage queryExpansionStage;

    public DefaultQueryUnderstandingPipeline(
            QueryClassifierAdapter classifierAdapter,
            NamedEntityExtractionAdapter entityExtractionAdapter,
            StructuredQueryRewriter rewriter,
            QueryIntentResolver intentResolver,
            ExpectedAnswerShapeResolver answerShapeResolver,
            AmbiguityAssessmentService ambiguityAssessmentService,
            QueryExpansionStage queryExpansionStage) {
        this.classifierAdapter = classifierAdapter;
        this.entityExtractionAdapter = entityExtractionAdapter;
        this.rewriter = rewriter;
        this.intentResolver = intentResolver;
        this.answerShapeResolver = answerShapeResolver;
        this.ambiguityAssessmentService = ambiguityAssessmentService;
        this.queryExpansionStage = queryExpansionStage;
    }

    @Override
    public QueryPlan buildPlan(ExecutionContext ctx) {
        List<String> notes = new ArrayList<>();

        String rawLiteral = ctx.userQuery() == null ? "" : ctx.userQuery();
        String effectiveInput =
                ctx.effectivePlanningInputText() == null || ctx.effectivePlanningInputText().isBlank()
                        ? rawLiteral
                        : ctx.effectivePlanningInputText();
        effectiveInput = ClarifiedPlanningInputResolver.resolveForPlanning(effectiveInput);

        // 1) Normalize (P11: only effective planning input)
        long t0 = System.nanoTime();
        NormalizedQuery normalized = normalize(effectiveInput);
        notes.add(stageNote("qu_normalize", "OK", msSince(t0),
                normalized.notes().isEmpty() ? "normalized" : String.join(",", normalized.notes())));

        // 1b) Query expansion (independent of retrieval; uses task LLM when enabled)
        long tExpand = System.nanoTime();
        QueryExpansionResult expansion = queryExpansionStage.expand(ctx, normalized.normalizedText());
        String downstreamQuery = expansion.downstreamQueryText();
        NormalizedQuery downstreamNormalized = new NormalizedQuery(normalized.rawUserQuery(), downstreamQuery, normalized.notes());
        String expandStatus;
        if (!ctx.resolved().toRagConfig().expansionEnabled()) {
            expandStatus = QU_NOTE_DISABLED;
        } else if (expansion.applied()) {
            expandStatus = "OK";
        } else if ("FAILED".equals(expansion.strategy())) {
            expandStatus = QU_NOTE_ERROR;
        } else {
            expandStatus = "OK";
        }
        notes.add(stageNote(
                "qu_expand",
                expandStatus,
                msSince(tExpand),
                "applied="
                        + expansion.applied()
                        + " strategy="
                        + expansion.strategy()
                        + " original="
                        + truncateForTrace(expansion.originalQuery())
                        + " expanded="
                        + truncateForTrace(expansion.expandedQuery())
                        + (expansion.traceNote().isBlank() ? "" : " note=" + expansion.traceNote())));

        // 2) Classify
        long t1 = System.nanoTime();
        QueryClassifierAdapter.ClassifierOutcome c = classifierAdapter.classify(ctx, downstreamNormalized.normalizedText());
        String classifyStatus = switch (c.classifierStatus()) {
            case OK -> "OK";
            case DISABLED -> QU_NOTE_DISABLED;
            case INVALID_OUTPUT, LOW_CONFIDENCE -> QU_NOTE_FALLBACK;
            case UNAVAILABLE, TIMEOUT, INVALID_REQUEST -> QU_NOTE_ERROR;
        };
        StringBuilder classifyDetail =
                new StringBuilder("classifierStatus=")
                        .append(c.classifierStatus().name())
                        .append(" classifierModelId=")
                        .append(c.classifierModelIdUsed())
                        .append(" classifierLabel=")
                        .append(c.classifierLabel());
        c.classifierConfidence()
                .ifPresent(conf -> classifyDetail.append(" classifierConfidence=").append(conf));
        c.classifierLabelSetHash()
                .ifPresent(hash -> classifyDetail.append(" classifierLabelSetHash=").append(hash));
        classifyDetail.append(" note=").append(c.note());
        notes.add(stageNote("qu_classify", classifyStatus, msSince(t1), classifyDetail.toString()));

        String classifierLabel = c.classifierLabel();
        Optional<QueryType> classifierQueryType = c.classifierQueryType();
        ClassifierStatus classifierStatus = c.classifierStatus();

        // 3) Extract entities
        long t2 = System.nanoTime();
        EntityExtractionResult entities = entityExtractionAdapter.extract(ctx, downstreamNormalized.normalizedText());
        String nerStatus;
        if (!ctx.resolved().toRagConfig().nerEnabled()) {
            nerStatus = QU_NOTE_DISABLED;
        } else if (!entities.notes().isEmpty()
                && entities.notes().get(0).startsWith(QU_NOTE_FALLBACK)) {
            nerStatus = QU_NOTE_ERROR;
        } else {
            nerStatus = "OK";
        }
        notes.add(stageNote("qu_extract_entities", nerStatus, msSince(t2),
                entities.notes().isEmpty() ? "entities_extracted" : String.join(";", entities.notes())));

        // 4) Rewrite
        long t3 = System.nanoTime();
        StructuredRewriteResult rewrite = rewriter.rewrite(
                ctx, downstreamNormalized, classifierLabel, classifierQueryType, classifierStatus, entities);
        String rewriteStatus = "OK";
        if (!ctx.resolved().toRagConfig().toolsEnabled()) {
            rewriteStatus = QU_NOTE_DISABLED;
        } else if (!rewrite.rewriteNotes().isEmpty()) {
            String first = rewrite.rewriteNotes().get(0).toUpperCase();
            if (first.startsWith(QU_NOTE_FALLBACK)) {
                rewriteStatus = QU_NOTE_ERROR;
            } else if (first.startsWith(QU_NOTE_DISABLED)) {
                rewriteStatus = QU_NOTE_DISABLED;
            }
        }
        notes.add(stageNote("qu_rewrite", rewriteStatus, msSince(t3),
                rewrite.rewriteNotes().isEmpty() ? "rewrite_applied=" + rewrite.rewriteApplied()
                        : String.join(";", rewrite.rewriteNotes())));

        // 5) Resolve intent
        long t4 = System.nanoTime();
        QueryIntent intent = intentResolver.resolve(
                downstreamNormalized, classifierQueryType, classifierLabel, classifierStatus, rewrite, entities);
        notes.add(stageNote("qu_resolve_intent", "OK", msSince(t4), "queryIntent=" + intent.name()));

        // 6) Resolve expected answer shape
        long t5 = System.nanoTime();
        ExpectedAnswerShape shape = answerShapeResolver.resolve(classifierQueryType, entities);
        notes.add(stageNote("qu_resolve_answer_shape", "OK", msSince(t5), "expectedAnswerShape=" + shape.name()));

        // 7) Assess ambiguity
        long t6 = System.nanoTime();
        AmbiguityAssessment ambiguity = ambiguityAssessmentService.assess(
                downstreamNormalized, classifierQueryType, classifierLabel, classifierStatus, rewrite, entities);
        notes.add(stageNote("qu_assess_ambiguity", "OK", msSince(t6), "ambiguityStatus=" + ambiguity.status().name()));

        // 8) Build QueryPlan
        Map<String, String> slots = QueryPlanSlotEnricher.enrich(
                downstreamNormalized.normalizedText(), classifierQueryType, rewrite.slotFilling());
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                rawLiteral,
                effectiveInput,
                normalized.normalizedText(),
                rewrite.rewrittenQueryText(),
                classifierLabel,
                classifierQueryType,
                classifierStatus,
                intent,
                slots,
                rewrite.targetEntities(),
                rewrite.targetAttributes(),
                entities,
                rewrite,
                shape,
                ambiguity,
                ctx.correlationId(),
                c.classifierModelIdUsed(),
                notes,
                expansion
        );
    }

    private static String truncateForTrace(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
    }

    private static NormalizedQuery normalize(String rawUserQuery) {
        String raw = rawUserQuery == null ? "" : rawUserQuery;
        String normalized = raw.strip().replaceAll("\\s+", " ");
        List<String> notes = new ArrayList<>();
        if (!raw.equals(normalized)) {
            notes.add("whitespace_normalized");
        }
        if (normalized.isBlank()) {
            notes.add("blank_query");
        }
        return new NormalizedQuery(raw, normalized, notes);
    }

    private static String stageNote(String stageName, String quStatus, long durationMs, String message) {
        return stageName
                + " qu_status=" + quStatus
                + " durationMs=" + durationMs
                + " message=" + (message == null ? "" : message);
    }

    private static long msSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}

