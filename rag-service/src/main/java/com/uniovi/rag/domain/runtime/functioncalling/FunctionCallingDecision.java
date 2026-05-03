package com.uniovi.rag.domain.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * FC policy decision when {@link FunctionCallingPolicyResolver} exposes at least one tool (§10.11 item 6).
 */
public record FunctionCallingDecision(
        FunctionCallingMode mode,
        FunctionCallingOutcome policyPlaceholderOutcome,
        boolean selected,
        List<DeterministicToolKind> exposedToolKinds,
        List<String> reasons,
        Optional<String> suppressionReason,
        String canonicalQueryText,
        Map<String, String> normalizedInputs) {

    public FunctionCallingDecision {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        exposedToolKinds = List.copyOf(Objects.requireNonNull(exposedToolKinds, "exposedToolKinds"));
        normalizedInputs = Map.copyOf(Objects.requireNonNull(normalizedInputs, "normalizedInputs"));
        suppressionReason = Objects.requireNonNullElseGet(suppressionReason, Optional::empty);
        canonicalQueryText = Objects.requireNonNull(canonicalQueryText, "canonicalQueryText");
    }
}
