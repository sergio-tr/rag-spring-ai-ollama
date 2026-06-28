package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class P15ParentCandidateSafetyPolicyTest {

    @Test
    void acceptsSafeCampaignParentWithToolFinal() {
        CampaignParentOutcome record =
                new CampaignParentOutcome(
                        "Una.",
                        "deterministic-tool",
                        false,
                        "DETERMINISTIC_TOOL_ROUTE",
                        false,
                        true,
                        "count_documents",
                        "TOOL_FINAL",
                        "COMPLETE",
                        "SAFE",
                        "true");
        RouteCandidateValidationResult validation = RouteCandidateValidationResult.accepted(0.8, "COMPLETE");

        assertThat(P15ParentCandidateSafetyPolicy.isBaselineEligible(Optional.of(record), validation))
                .isTrue();
    }

    @Test
    void rejectsUnsafeCampaignParentStatus() {
        CampaignParentOutcome record =
                new CampaignParentOutcome(
                        "unsafe",
                        "workflow",
                        false,
                        "RETRIEVAL_WORKFLOW_ROUTE",
                        false,
                        false,
                        "",
                        "GENERATED",
                        "PARTIAL",
                        "UNSAFE",
                        "true");
        RouteCandidateValidationResult validation = RouteCandidateValidationResult.accepted(0.8, "PARTIAL");

        assertThat(P15ParentCandidateSafetyPolicy.isBaselineEligible(Optional.of(record), validation))
                .isFalse();
    }

    @Test
    void trustedCampaignValidation_acceptsGetDurationParentWithoutDayEntityInAnswer() {
        CampaignParentOutcome record =
                new CampaignParentOutcome(
                        "La duración de la reunión fue de 1 hora y 30 minutos.",
                        "ChunkDenseRagWorkflow",
                        true,
                        "RETRIEVAL_WORKFLOW_ROUTE",
                        false,
                        false,
                        "none",
                        "GENERATED",
                        "COMPLETE",
                        "SAFE",
                        "true");

        Optional<RouteCandidateValidationResult> trusted =
                P15ParentCandidateSafetyPolicy.trustedCampaignValidation(record);

        assertThat(trusted).isPresent();
        assertThat(trusted.get().safe()).isTrue();
    }

    @Test
    void trustedCampaignValidation_rejectsUnsafeCampaignParent() {
        CampaignParentOutcome record =
                new CampaignParentOutcome(
                        "unsafe",
                        "workflow",
                        false,
                        "RETRIEVAL_WORKFLOW_ROUTE",
                        false,
                        false,
                        "",
                        "GENERATED",
                        "PARTIAL",
                        "UNSAFE",
                        "true");

        assertThat(P15ParentCandidateSafetyPolicy.trustedCampaignValidation(record)).isEmpty();
    }
}
