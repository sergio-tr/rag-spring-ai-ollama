package com.uniovi.rag.domain.runtime.advisor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorAndPackingDomainTest {

    @Test
    void packedContextSet_requiresBlockCountToMatchListSize() {
        List<PackedContextBlock> blocks =
                List.of(sampleBlock("b1"), sampleBlock("b2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PackedContextSet(blocks, "s", 1, 1, List.of(), ""));
        PackedContextSet ok = new PackedContextSet(blocks, "s", 1, 2, List.of("n"), "ctx");
        assertEquals(2, ok.totalBlockCount());
    }

    @Test
    void packedContextSet_defensiveCopiesAndNormalizesNullStrings() {
        UUID id = UUID.randomUUID();
        PackedContextBlock block = new PackedContextBlock(null, null, "bid", id, null, 0, List.of("note"));
        assertEquals("", block.sourceId());
        assertEquals("", block.documentId());
        assertEquals("", block.blockText());
        assertThrows(NullPointerException.class, () -> new PackedContextBlock("s", "d", null, id, "t", 0, List.of()));
        assertThrows(NullPointerException.class, () -> new PackedContextBlock("s", "d", "bid", null, "t", 0, List.of()));
        assertThrows(NullPointerException.class, () -> new PackedContextBlock("s", "d", "bid", id, "t", 0, null));

        PackedContextSet set =
                new PackedContextSet(List.of(block), null, 0, 1, List.of(), null);
        assertEquals("", set.packingStrategyId());
        assertEquals("", set.promptContextText());
        assertThrows(NullPointerException.class, () -> new PackedContextSet(null, "s", 0, 0, List.of(), ""));
        assertThrows(NullPointerException.class, () -> new PackedContextSet(List.of(), "s", 0, 0, null, ""));
    }

    @Test
    void advisorDecision_enforcesExecutableKindRulesWhenSelected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdvisorDecision(
                                AdvisorMode.ENABLED,
                                true,
                                List.of(AdvisorKind.MEMORY_ADVISOR),
                                "",
                                List.of(),
                                Optional.empty()));
        AdvisorDecision ok =
                new AdvisorDecision(
                        AdvisorMode.ENABLED,
                        true,
                        AdvisorDecision.EXECUTABLE_KINDS_5_2,
                        "q",
                        List.of(),
                        Optional.empty());
        assertTrue(ok.selected());
    }

    @Test
    void advisorDecision_whenNotSelectedExecutableKindsMustBeEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdvisorDecision(
                                AdvisorMode.DISABLED,
                                false,
                                List.of(AdvisorKind.RETRIEVAL_ADVISOR),
                                "",
                                List.of(),
                                Optional.empty()));
    }

    @Test
    void advisorExecutionResult_factoriesSetExpectedOutcomes() {
        PackedContextSet packed =
                new PackedContextSet(List.of(sampleBlock("x")), "p", 0, 1, List.of(), "t");
        AdvisorExecutionResult ok = AdvisorExecutionResult.success(packed);
        assertEquals(AdvisorOutcome.EXECUTED_SUCCESS, ok.outcome());
        assertTrue(ok.packedContextSet().isPresent());

        assertEquals(
                AdvisorOutcome.EXECUTED_FAILED_RETRIEVAL,
                AdvisorExecutionResult.failedRetrieval(List.of("n")).outcome());
        assertEquals(
                AdvisorOutcome.EXECUTED_FAILED_PACKING,
                AdvisorExecutionResult.failedPacking(List.of("n")).outcome());
        assertEquals(
                AdvisorOutcome.FAILED_RESERVED_KIND,
                AdvisorExecutionResult.failedReservedKind(List.of("n")).outcome());
    }

    private static PackedContextBlock sampleBlock(String blockId) {
        return new PackedContextBlock("src", "doc", blockId, UUID.randomUUID(), "text", 0, List.of());
    }
}
