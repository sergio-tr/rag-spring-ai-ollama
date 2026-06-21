package com.uniovi.rag.domain.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.Objects;
import java.util.Optional;

/** Validated or rejected function-call proposal before tool execution. */
public record FunctionCallProposal(
        FunctionProposalMode proposalMode,
        String functionName,
        Optional<DeterministicToolKind> toolKind,
        String arguments,
        boolean argumentsValid,
        Optional<String> schemaValidationError,
        boolean repairAttempted,
        boolean repairSucceeded,
        Optional<Double> proposalConfidence,
        Optional<DeterministicEvidenceLevel> proposalEvidenceLevel,
        FunctionProposalSource proposalSource) {

    public FunctionCallProposal {
        functionName = functionName != null ? functionName : "";
        arguments = arguments != null ? arguments : "";
        toolKind = Objects.requireNonNullElseGet(toolKind, Optional::empty);
        schemaValidationError = Objects.requireNonNullElseGet(schemaValidationError, Optional::empty);
        proposalConfidence = Objects.requireNonNullElseGet(proposalConfidence, Optional::empty);
        proposalEvidenceLevel = Objects.requireNonNullElseGet(proposalEvidenceLevel, Optional::empty);
        proposalSource = proposalSource != null ? proposalSource : FunctionProposalSource.NONE;
        proposalMode = proposalMode != null ? proposalMode : FunctionProposalMode.NONE;
    }

    public static FunctionCallProposal none(String validationError) {
        return new FunctionCallProposal(
                FunctionProposalMode.NONE,
                "",
                Optional.empty(),
                "",
                false,
                Optional.ofNullable(validationError).filter(s -> !s.isBlank()),
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                FunctionProposalSource.NONE);
    }

    public boolean hasToolKind() {
        return toolKind.isPresent();
    }
}
