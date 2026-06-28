package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorKind;
import com.uniovi.rag.domain.runtime.advisor.AdvisorMode;
import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdvisorStrategyTest {

    @Test
    void execute_rejects_unselected_decision() {
        AdvisorStrategy strategy =
                new AdvisorStrategy(Mockito.mock(RetrievalAdvisor.class), Mockito.mock(ContextPackingAdvisor.class));
        AdvisorDecision d =
                new AdvisorDecision(
                        AdvisorMode.ENABLED, false, List.of(), "q", List.of(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> strategy.execute(null, null, "w", d));
    }

    @Test
    void execute_success_returns_packed() {
        RetrievalAdvisor retrieval = Mockito.mock(RetrievalAdvisor.class);
        ContextPackingAdvisor packing = Mockito.mock(ContextPackingAdvisor.class);
        AdvisorStrategy strategy = new AdvisorStrategy(retrieval, packing);

        CuratedContextSet curated =
                new CuratedContextSet(
                        List.of(),
                        "",
                        new CompressionOutcome(0, 0, 0, List.of()),
                        List.of(),
                        new RetrievalDiagnostics(
                                RetrievalMode.DENSE_ONLY,
                                Optional.empty(),
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                false,
                                List.of(),
                                List.of(),
                                Optional.empty(), 0, 0, false, 0),
                        List.of(),
                        List.of());
        PackedContextSet packed =
                new PackedContextSet(
                        List.of(),
                        ContextPackingAdvisor.PACKING_STRATEGY_ID,
                        0,
                        0,
                        List.of(),
                        "");
        when(retrieval.retrieve(any(), any(QueryPlan.class), eq("ChunkDenseRagWorkflow"))).thenReturn(curated);
        when(packing.pack(any(), any(QueryPlan.class), eq(curated), eq("ChunkDenseRagWorkflow")))
                .thenReturn(packed);

        AdvisorDecision decision =
                new AdvisorDecision(
                        AdvisorMode.ENABLED,
                        true,
                        AdvisorDecision.EXECUTABLE_KINDS_5_2,
                        "rewritten",
                        List.of(),
                        Optional.empty());
        AdvisorExecutionResult r =
                strategy.execute(Mockito.mock(ExecutionContext.class), Mockito.mock(QueryPlan.class), "ChunkDenseRagWorkflow", decision);
        assertEquals(AdvisorOutcome.EXECUTED_SUCCESS, r.outcome());
        assertTrue(r.shortCircuitedContextPrep());
        assertTrue(r.packedContextSet().isPresent());
    }

    @Test
    void execute_retrieval_failure() {
        RetrievalAdvisor retrieval = Mockito.mock(RetrievalAdvisor.class);
        ContextPackingAdvisor packing = Mockito.mock(ContextPackingAdvisor.class);
        AdvisorStrategy strategy = new AdvisorStrategy(retrieval, packing);
        when(retrieval.retrieve(any(), any(QueryPlan.class), eq("ChunkDenseRagWorkflow")))
                .thenThrow(new RuntimeException("boom"));

        AdvisorDecision decision =
                new AdvisorDecision(
                        AdvisorMode.ENABLED,
                        true,
                        AdvisorDecision.EXECUTABLE_KINDS_5_2,
                        "rewritten",
                        List.of(),
                        Optional.empty());
        AdvisorExecutionResult r =
                strategy.execute(Mockito.mock(ExecutionContext.class), Mockito.mock(QueryPlan.class), "ChunkDenseRagWorkflow", decision);
        assertEquals(AdvisorOutcome.EXECUTED_FAILED_RETRIEVAL, r.outcome());
        assertFalse(r.shortCircuitedContextPrep());
    }

    @Test
    void execute_packing_failure_returns_failed_packing() {
        RetrievalAdvisor retrieval = Mockito.mock(RetrievalAdvisor.class);
        ContextPackingAdvisor packing = Mockito.mock(ContextPackingAdvisor.class);
        AdvisorStrategy strategy = new AdvisorStrategy(retrieval, packing);

        CuratedContextSet curated =
                new CuratedContextSet(
                        List.of(),
                        "",
                        new CompressionOutcome(0, 0, 0, List.of()),
                        List.of(),
                        new RetrievalDiagnostics(
                                RetrievalMode.DENSE_ONLY,
                                Optional.empty(),
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                false,
                                List.of(),
                                List.of(),
                                Optional.empty(), 0, 0, false, 0),
                        List.of(),
                        List.of());
        when(retrieval.retrieve(any(), any(QueryPlan.class), eq("ChunkDenseRagWorkflow"))).thenReturn(curated);
        when(packing.pack(any(), any(QueryPlan.class), eq(curated), eq("ChunkDenseRagWorkflow")))
                .thenThrow(new RuntimeException("pack boom"));

        AdvisorDecision decision =
                new AdvisorDecision(
                        AdvisorMode.ENABLED,
                        true,
                        AdvisorDecision.EXECUTABLE_KINDS_5_2,
                        "rewritten",
                        List.of(),
                        Optional.empty());
        AdvisorExecutionResult r =
                strategy.execute(
                        Mockito.mock(ExecutionContext.class),
                        Mockito.mock(QueryPlan.class),
                        "ChunkDenseRagWorkflow",
                        decision);
        assertEquals(AdvisorOutcome.EXECUTED_FAILED_PACKING, r.outcome());
        assertFalse(r.shortCircuitedContextPrep());
    }

    @Test
    void execute_reserved_kind_in_executable_list_returns_failed_reserved_kind() {
        RetrievalAdvisor retrieval = Mockito.mock(RetrievalAdvisor.class);
        ContextPackingAdvisor packing = Mockito.mock(ContextPackingAdvisor.class);
        AdvisorStrategy strategy = new AdvisorStrategy(retrieval, packing);

        AdvisorDecision bad = Mockito.mock(AdvisorDecision.class);
        when(bad.selected()).thenReturn(true);
        when(bad.executableKinds()).thenReturn(List.of(AdvisorKind.MEMORY_ADVISOR, AdvisorKind.CONTEXT_PACKING_ADVISOR));

        AdvisorExecutionResult r =
                strategy.execute(
                        Mockito.mock(ExecutionContext.class),
                        Mockito.mock(QueryPlan.class),
                        "ChunkDenseRagWorkflow",
                        bad);
        assertEquals(AdvisorOutcome.FAILED_RESERVED_KIND, r.outcome());
        Mockito.verifyNoInteractions(retrieval);
    }
}
