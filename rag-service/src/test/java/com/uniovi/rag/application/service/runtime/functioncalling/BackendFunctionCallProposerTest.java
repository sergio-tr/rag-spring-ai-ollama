package com.uniovi.rag.application.service.runtime.functioncalling;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalSource;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolBenchmarkContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackendFunctionCallProposerTest {

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
    void productionLikeMode_doesNotUseOracleEvenWhenExpectedTypePresent() {
        DeterministicToolBenchmarkContext.setExpectedQueryType("COUNT_DOCUMENTS");
        QueryPlan plan = countPlan("¿Se habló de la radiación solar en alguna reunión?");
        FunctionCallingDecision decision = decision(List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
        FunctionCallProposal proposal = proposer.propose(plan, decision);
        assertThat(proposal.proposalSource()).isNotEqualTo(FunctionProposalSource.LAB_ORACLE);
    }

    @Test
    void proposesCountDocumentsFromQueryShape() {
        QueryPlan plan = countPlan("¿Se habló de la radiación solar en alguna reunión?");
        FunctionCallingDecision decision = decision(List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
        FunctionCallProposal proposal = proposer.propose(plan, decision);
        assertThat(proposal.proposalMode()).isEqualTo(FunctionProposalMode.BACKEND_DETERMINISTIC);
        assertThat(proposal.proposalSource()).isEqualTo(FunctionProposalSource.QUERY_SHAPE);
        assertThat(proposal.argumentsValid()).isTrue();
        assertThat(proposal.toolKind()).contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        assertThat(proposal.functionName()).isEqualTo("countDocuments");
    }

    @Test
    void returnsNoneWhenToolNotExposed() {
        QueryPlan plan = countPlan("¿Se habló de la radiación solar en alguna reunión?");
        FunctionCallingDecision decision = decision(List.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL));
        FunctionCallProposal proposal = proposer.propose(plan, decision);
        assertThat(proposal.proposalMode()).isEqualTo(FunctionProposalMode.NONE);
        assertThat(proposal.argumentsValid()).isFalse();
    }

    @Test
    void proposeFromOptionalModelJsonRepairsInvalidQuery() {
        QueryPlan plan = countPlan("how many actas mention reforma?");
        FunctionCallingDecision decision = decision(List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
        FunctionCallProposal proposal =
                proposer.proposeFromOptionalModelJson(
                        "{\"query\":\"wrong\"}",
                        DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                        plan,
                        decision);
        assertThat(proposal.proposalMode()).isEqualTo(FunctionProposalMode.MODEL_JSON);
        assertThat(proposal.repairAttempted()).isTrue();
        assertThat(proposal.repairSucceeded()).isTrue();
        assertThat(proposal.argumentsValid()).isTrue();
    }

    private static FunctionCallingDecision decision(List<DeterministicToolKind> kinds) {
        return new FunctionCallingDecision(
                FunctionCallingMode.ENABLED,
                FunctionCallingOutcome.NOT_APPLICABLE,
                true,
                kinds,
                List.of("exposed"),
                Optional.empty(),
                "q",
                Map.of());
    }

    private static QueryPlan countPlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                query,
                query,
                query,
                query,
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, "test"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "cid",
                "",
                List.of());
    }
}
