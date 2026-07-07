package com.uniovi.rag.domain.chat;

import java.util.List;

public record PresetDraftCompatibilityResult(
        RuntimePresetIndexRequirements indexRequirements,
        boolean compatibleWithActiveIndex,
        String compatibilityStatus,
        List<PresetValidationIssue> blockingIssues) {

    public static PresetDraftCompatibilityResult unavailable() {
        return new PresetDraftCompatibilityResult(
                null,
                false,
                null,
                List.of(
                        new PresetValidationIssue(
                                "RUNTIME_STATE_UNAVAILABLE",
                                "Runtime configuration could not be validated.")));
    }
}
