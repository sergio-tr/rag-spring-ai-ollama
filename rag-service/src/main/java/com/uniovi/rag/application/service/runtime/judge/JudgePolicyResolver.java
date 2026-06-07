package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeDecision;
import com.uniovi.rag.domain.runtime.judge.JudgeKind;
import com.uniovi.rag.domain.runtime.judge.JudgeMode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Default judge behaviour when {@code judgeEnabled=true}: evaluate the workflow candidate answer, accept or reject
 * without retry when retries are disallowed, and perform at most one bounded regeneration retry when the candidate
 * comes from the primary workflow path ({@link JudgeCandidateSource#WORKFLOW}).
 */
@Service
public class JudgePolicyResolver {

    public JudgeDecision resolve(
            ExecutionContext ctx,
            QueryPlan plan,
            AdaptiveRouteKind routeKind,
            String workflowName,
            JudgeCandidateSource candidateSource
    ) {
        var rag = ctx.resolved().toRagConfig();
        List<String> reasons = new ArrayList<>();

        if (!rag.judgeEnabled()) {
            reasons.add("judgeEnabled=false");
            return new JudgeDecision(
                    JudgeMode.DISABLED,
                    JudgeKind.POST_ANSWER_JUDGE,
                    candidateSource,
                    false,
                    false,
                    List.copyOf(reasons),
                    List.of());
        }

        reasons.add("judgeEnabled=true");
        reasons.add("routeKind=" + routeKind);
        reasons.add("workflowName=" + (workflowName == null ? "" : workflowName));
        reasons.add("candidateSource=" + candidateSource);

        boolean retryAllowed = candidateSource == JudgeCandidateSource.WORKFLOW;
        if (!retryAllowed) {
            reasons.add("retryForbiddenForSource=" + candidateSource);
        }

        return new JudgeDecision(
                JudgeMode.ENABLED,
                JudgeKind.POST_ANSWER_JUDGE,
                candidateSource,
                true,
                retryAllowed,
                List.copyOf(reasons),
                List.of());
    }
}

