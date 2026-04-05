package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagEffectiveFeatures;
import com.uniovi.rag.application.model.DraftAndContext;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.model.ReasoningPreOutput;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * <p>Single synthesis core used for every answer: metadata guard → tool shortcuts → routed tools → LLM (+RAG).
 * Reasoning ({@link com.uniovi.rag.service.reasoning.ReasoningStrategy}) only adds pre/post/ranker
 * <em>around</em> this core — it does not skip or duplicate these steps.</p>
 *
 * <p>Call {@link #synthesizeCore(PreparedQuery, ReasoningPreOutput)} with {@code null} pre when reasoning is off;
 * with a non-null pre-output when reasoning is on (thought/plan may enrich the LLM branch only).</p>
 */
public final class ResponseSynthesisPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResponseSynthesisPipeline.class);

    private final RagFeatureConfiguration featureConfig;
    private final DateExistenceGuard dateExistenceGuard;
    private final ToolRoutingService toolRouting;
    private final AnswerGenerationKernel kernel;

    public ResponseSynthesisPipeline(
            RagFeatureConfiguration featureConfig,
            DateExistenceGuard dateExistenceGuard,
            ToolRoutingService toolRouting,
            AnswerGenerationKernel kernel) {
        this.featureConfig = featureConfig;
        this.dateExistenceGuard = dateExistenceGuard;
        this.toolRouting = toolRouting;
        this.kernel = kernel;
    }

    /**
     * The only path that produces an answer from {@link PreparedQuery}. Same code path with or without reasoning;
     * {@code reasoningPre} is only used to optionally inject a planning block into the LLM+RAG step.
     */
    public CoreSynthesisResult synthesizeCore(PreparedQuery pq, ReasoningPreOutput reasoningPre) {
        String expandedQuery = pq.expandedQuery();
        JSONObject nerEntities = pq.nerEntities();
        QueryType queryType = pq.queryType();

        Optional<CoreSynthesisResult> metadata = tryMetadataDateGuard(expandedQuery, queryType, nerEntities);
        if (metadata.isPresent()) {
            return metadata.get();
        }

        var preferTool = toolRouting.tryPreferToolForDate(queryType, nerEntities, expandedQuery);
        if (preferTool.isPresent()) {
            return fromToolQueryResponse(preferTool.get(), CoreSynthesisResult.Kind.TOOL);
        }

        var mainTools = toolRouting.tryMainToolsBlock(queryType, expandedQuery);
        if (mainTools.isPresent()) {
            return fromToolQueryResponse(mainTools.get(), CoreSynthesisResult.Kind.TOOL);
        }

        ToolResult routed = toolRouting.tryToolRoute(expandedQuery, nerEntities, queryType);
        if (routed != null) {
            log.info("Response generated with tool {}: {}", routed.source(), routed.result());
            return new CoreSynthesisResult(
                    new DraftAndContext(routed.result(), routed.result()),
                    CoreSynthesisResult.Kind.TOOL,
                    routed.source());
        }

        log.info("Response generated with model directly (fallback; see fallback_reason in log above if tools were enabled)");
        return synthesizeLlmBranch(expandedQuery, nerEntities, queryType, reasoningPre);
    }

    private CoreSynthesisResult synthesizeLlmBranch(
            String expandedQuery,
            JSONObject nerEntities,
            QueryType queryType,
            ReasoningPreOutput reasoningPre) {
        if (reasoningPre != null
                && reasoningPre.thoughtOrPlan() != null
                && !reasoningPre.thoughtOrPlan().isBlank()) {
            DraftAndContext d = kernel.askModelWithPreStep(
                    expandedQuery,
                    nerEntities,
                    queryType,
                    reasoningPre.thoughtOrPlan());
            log.info("Response generated via askModelWithPreStep (reasoning pre-plan)");
            return new CoreSynthesisResult(d, CoreSynthesisResult.Kind.LLM, null);
        }
        String answer = kernel.askModel(expandedQuery, nerEntities, queryType);
        log.info("Response generated with model directly: {}", answer);
        return new CoreSynthesisResult(
                new DraftAndContext(answer != null ? answer : "", ""),
                CoreSynthesisResult.Kind.LLM,
                null);
    }

    /**
     * Used when reasoning rejects draft quality: repeat plain LLM path without injecting reasoning pre-plan.
     */
    public String fallbackPlainLlm(PreparedQuery pq) {
        return kernel.askModel(pq.expandedQuery(), pq.nerEntities(), pq.queryType());
    }

    private Optional<CoreSynthesisResult> tryMetadataDateGuard(
            String expandedQuery,
            QueryType queryType,
            JSONObject nerEntities) {
        if (!RagEffectiveFeatures.metadataEnabled(featureConfig) || queryType == null) {
            return Optional.empty();
        }
        var guardResult = dateExistenceGuard.checkNoActaForDate(expandedQuery, queryType, nerEntities);
        if (guardResult.isEmpty()) {
            return Optional.empty();
        }
        String text = guardResult.get().result();
        String source = guardResult.get().source();
        log.info("DateExistenceGuard: returning no-acta response for date-dependent query (queryType={})", queryType);
        log.info("Response generated with tool {}: {}", source, text);
        return Optional.of(new CoreSynthesisResult(
                new DraftAndContext(text, text),
                CoreSynthesisResult.Kind.METADATA_GUARD,
                source));
    }

    private static CoreSynthesisResult fromToolQueryResponse(QueryResponse qr, CoreSynthesisResult.Kind kind) {
        String answer = qr.getAnswer();
        String a = answer != null ? answer : "";
        return new CoreSynthesisResult(new DraftAndContext(a, a), kind, qr.getToolUsed());
    }

}
