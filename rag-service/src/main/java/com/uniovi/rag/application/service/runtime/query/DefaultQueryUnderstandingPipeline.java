package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
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

    private final QueryClassifierAdapter classifierAdapter;
    private final NamedEntityExtractionAdapter entityExtractionAdapter;
    private final StructuredQueryRewriter rewriter;
    private final QueryIntentResolver intentResolver;
    private final ExpectedAnswerShapeResolver answerShapeResolver;
    private final AmbiguityAssessmentService ambiguityAssessmentService;

    public DefaultQueryUnderstandingPipeline(
            QueryClassifierAdapter classifierAdapter,
            NamedEntityExtractionAdapter entityExtractionAdapter,
            StructuredQueryRewriter rewriter,
            QueryIntentResolver intentResolver,
            ExpectedAnswerShapeResolver answerShapeResolver,
            AmbiguityAssessmentService ambiguityAssessmentService) {
        this.classifierAdapter = classifierAdapter;
        this.entityExtractionAdapter = entityExtractionAdapter;
        this.rewriter = rewriter;
        this.intentResolver = intentResolver;
        this.answerShapeResolver = answerShapeResolver;
        this.ambiguityAssessmentService = ambiguityAssessmentService;
    }

    @Override
    public QueryPlan buildPlan(ExecutionContext ctx) {
        List<String> notes = new ArrayList<>();

        String rawLiteral = ctx.userQuery() == null ? "" : ctx.userQuery();
        String effectiveInput =
                ctx.effectivePlanningInputText() == null || ctx.effectivePlanningInputText().isBlank()
                        ? rawLiteral
                        : ctx.effectivePlanningInputText();

        // 1) Normalize (P11: only effective planning input)
        long t0 = System.nanoTime();
        NormalizedQuery normalized = normalize(effectiveInput);
        notes.add(stageNote("qu_normalize", "OK", msSince(t0),
                normalized.notes().isEmpty() ? "normalized" : String.join(",", normalized.notes())));

        // 2) Classify
        long t1 = System.nanoTime();
        QueryClassifierAdapter.ClassifierOutcome c = classifierAdapter.classify(ctx, normalized.normalizedText());
        String classifyStatus = switch (c.classifierStatus()) {
            case OK -> "OK";
            case DISABLED -> "DISABLED";
            case INVALID_OUTPUT -> "FALLBACK";
            case UNAVAILABLE -> "ERROR";
        };
        notes.add(stageNote("qu_classify", classifyStatus, msSince(t1),
                "classifierStatus=" + c.classifierStatus().name()
                        + " classifierLabel=" + c.classifierLabel()
                        + " note=" + c.note()));

        String classifierLabel = c.classifierLabel();
        Optional<QueryType> classifierQueryType = c.classifierQueryType();
        ClassifierStatus classifierStatus = c.classifierStatus();

        // 3) Extract entities
        long t2 = System.nanoTime();
        EntityExtractionResult entities = entityExtractionAdapter.extract(ctx, normalized.normalizedText());
        String nerStatus;
        if (!ctx.resolved().toRagConfig().nerEnabled()) {
            nerStatus = "DISABLED";
        } else if (!entities.notes().isEmpty() && entities.notes().get(0).startsWith("FALLBACK")) {
            nerStatus = "ERROR";
        } else {
            nerStatus = "OK";
        }
        notes.add(stageNote("qu_extract_entities", nerStatus, msSince(t2),
                entities.notes().isEmpty() ? "entities_extracted" : String.join(";", entities.notes())));

        // 4) Rewrite
        long t3 = System.nanoTime();
        StructuredRewriteResult rewrite = rewriter.rewrite(
                ctx, normalized, classifierLabel, classifierQueryType, classifierStatus, entities);
        String rewriteStatus = "OK";
        if (!ctx.resolved().toRagConfig().toolsEnabled()) {
            rewriteStatus = "DISABLED";
        } else if (!rewrite.rewriteNotes().isEmpty()) {
            String first = rewrite.rewriteNotes().get(0).toUpperCase();
            if (first.startsWith("FALLBACK")) {
                rewriteStatus = "ERROR";
            } else if (first.startsWith("DISABLED")) {
                rewriteStatus = "DISABLED";
            }
        }
        notes.add(stageNote("qu_rewrite", rewriteStatus, msSince(t3),
                rewrite.rewriteNotes().isEmpty() ? "rewrite_applied=" + rewrite.rewriteApplied()
                        : String.join(";", rewrite.rewriteNotes())));

        // 5) Resolve intent
        long t4 = System.nanoTime();
        QueryIntent intent = intentResolver.resolve(
                normalized, classifierQueryType, classifierLabel, classifierStatus, rewrite, entities);
        notes.add(stageNote("qu_resolve_intent", "OK", msSince(t4), "queryIntent=" + intent.name()));

        // 6) Resolve expected answer shape
        long t5 = System.nanoTime();
        ExpectedAnswerShape shape = answerShapeResolver.resolve(classifierQueryType, entities);
        notes.add(stageNote("qu_resolve_answer_shape", "OK", msSince(t5), "expectedAnswerShape=" + shape.name()));

        // 7) Assess ambiguity
        long t6 = System.nanoTime();
        AmbiguityAssessment ambiguity = ambiguityAssessmentService.assess(
                normalized, classifierQueryType, classifierLabel, classifierStatus, rewrite, entities);
        notes.add(stageNote("qu_assess_ambiguity", "OK", msSince(t6), "ambiguityStatus=" + ambiguity.status().name()));

        // 8) Build QueryPlan
        Map<String, String> slots = rewrite.slotFilling();
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
                notes
        );
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

