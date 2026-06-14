package com.uniovi.rag.application.service.runtime.functioncalling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.metrics.GoldSubsetManifest;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolApplicability;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolBenchmarkContext;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoldSubsetFunctionCallingProposerTest {

    private final BackendFunctionCallProposer proposer = new BackendFunctionCallProposer();

    @BeforeEach
    void setUp() {
        DeterministicToolBenchmarkContext.clearRunScope();
    }

    @AfterEach
    void tearDown() {
        DeterministicToolBenchmarkContext.clearRunScope();
    }

    @Test
    void goldSubsetApplicableItems_produceValidBackendProposalsWithoutOracle() throws Exception {
        GoldSubsetManifest manifest =
                new ObjectMapper()
                        .readValue(
                                getClass().getResourceAsStream("/evaluation/gold-subset-v1.json"),
                                GoldSubsetManifest.class);
        int applicable = 0;
        int valid = 0;
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            QueryType expected = QueryType.valueOf(entry.queryTypeExpected().trim());
            if (!DeterministicToolApplicability.isApplicableQueryType(expected)) {
                continue;
            }
            applicable++;
            QueryPlan plan = weakLivePlan(entry.question());
            Optional<DeterministicToolKind> kind =
                    DeterministicToolApplicability.toolKindForQueryType(expected);
            if (kind.isEmpty()) {
                continue;
            }
            FunctionCallingDecision decision =
                    new FunctionCallingDecision(
                            FunctionCallingMode.ENABLED,
                            FunctionCallingOutcome.NOT_APPLICABLE,
                            true,
                            List.of(kind.get()),
                            List.of("exposed"),
                            Optional.empty(),
                            plan.rewrittenQueryText(),
                            Map.of());
            FunctionCallProposal proposal = proposer.propose(plan, decision);
            if (proposal.proposalMode() == FunctionProposalMode.BACKEND_DETERMINISTIC && proposal.argumentsValid()) {
                valid++;
            }
        }
        assertThat(applicable).isGreaterThanOrEqualTo(12);
        assertThat(valid * 100.0 / applicable).isGreaterThanOrEqualTo(60.0);
    }

    private static QueryPlan weakLivePlan(String question) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                question,
                question,
                question,
                question,
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.LOW_CONFIDENCE,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(question, "simulated-live"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "cid",
                "",
                new ArrayList<>());
    }
}
