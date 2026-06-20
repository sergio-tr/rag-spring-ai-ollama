package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;

/** Export-safe finalAnswerSource values for verbatim parent preset replay. */
public final class ParentFinalAnswerSources {

    public static final String PARENT_P7_FINAL = "PARENT_P7_FINAL";
    public static final String PARENT_P6_FINAL = "PARENT_P6_FINAL";

    private ParentFinalAnswerSources() {}

    public static String forPreset(RagExperimentalPresetCode preset) {
        if (preset == RagExperimentalPresetCode.P6) {
            return PARENT_P6_FINAL;
        }
        return PARENT_P7_FINAL;
    }
}
