package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorMode;
import com.uniovi.rag.domain.runtime.advisor.AdvisorSuppressionReason;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether advisor execution is allowed; does not call retrieval or LLMs.
 */
@Service
public class AdvisorPolicyResolver {

    public AdvisorDecision resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        RagConfig rag = ctx.resolved().toRagConfig();
        String canonical = plan.rewrittenQueryText();
        List<String> reasons = new ArrayList<>();

        if (!rag.useAdvisor()) {
            reasons.add("useAdvisor=false");
            return new AdvisorDecision(
                    AdvisorMode.DISABLED, false, List.of(), canonical, reasons, Optional.of(AdvisorSuppressionReason.DISABLED_BY_CONFIG));
        }
        if (!isDenseAdvisorWorkflow(workflowName)) {
            reasons.add("workflow_not_dense_advisor=" + workflowName);
            return new AdvisorDecision(
                    AdvisorMode.ENABLED,
                    false,
                    List.of(),
                    canonical,
                    reasons,
                    Optional.of(AdvisorSuppressionReason.WORKFLOW_NOT_SUPPORTED));
        }
        if (plan.ambiguityAssessment().status() != AmbiguityStatus.SUFFICIENT) {
            reasons.add("ambiguity_not_sufficient");
            return new AdvisorDecision(
                    AdvisorMode.ENABLED,
                    false,
                    List.of(),
                    canonical,
                    reasons,
                    Optional.of(AdvisorSuppressionReason.SUPPRESSED_BY_AMBIGUITY));
        }

        reasons.add("advisor_execution_allowed");
        return new AdvisorDecision(
                AdvisorMode.ENABLED, true, AdvisorDecision.EXECUTABLE_KINDS_5_2, canonical, reasons, Optional.empty());
    }

    private static boolean isDenseAdvisorWorkflow(String workflowName) {
        return "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }
}
