package com.uniovi.rag.domain.chat;

import java.util.List;

public final class PresetCatalogCompatibility {

    private PresetCatalogCompatibility() {}

    public static PresetIndexCompatibility assess(
            PresetDraftCompatibilityResult draft, boolean catalogSelectable, String catalogSupportReason) {
        if (draft == null) {
            boolean selectable = catalogSelectable;
            return new PresetIndexCompatibility(
                    selectable,
                    selectable ? null : "PRESET_NOT_SELECTABLE",
                    selectable ? null : catalogSupportReason,
                    null,
                    false);
        }

        boolean indexOk = draft.compatibleWithActiveIndex();
        List<PresetValidationIssue> blocking =
                draft.blockingIssues() != null ? draft.blockingIssues() : List.of();

        String disabledReasonCode = null;
        String disabledReason = null;

        if (!catalogSelectable) {
            disabledReasonCode = "PRESET_NOT_SELECTABLE";
            disabledReason =
                    catalogSupportReason != null && !catalogSupportReason.isBlank()
                            ? catalogSupportReason
                            : "This configuration is not selectable in Chat.";
        } else if (!indexOk) {
            disabledReasonCode =
                    draft.compatibilityStatus() != null ? draft.compatibilityStatus() : "INDEX_INCOMPATIBLE";
            disabledReason = "Create or reindex the project with a compatible index profile.";
        } else if (!blocking.isEmpty()) {
            PresetValidationIssue first = blocking.getFirst();
            disabledReasonCode = first.code();
            disabledReason = first.message();
        }

        boolean selectable = catalogSelectable && indexOk && blocking.isEmpty();
        return new PresetIndexCompatibility(
                selectable,
                disabledReasonCode,
                disabledReason,
                draft.indexRequirements(),
                indexOk);
    }
}
