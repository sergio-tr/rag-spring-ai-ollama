package com.uniovi.rag.application.service.evaluation.provenance;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable build/git/environment identifiers attached to evaluation exports. */
public record EvaluationBuildMetadata(String gitSha, String buildId, String environmentLabel) {

    public static final String UNKNOWN = "unknown";

    public EvaluationBuildMetadata {
        gitSha = orUnknown(gitSha);
        buildId = orUnknown(buildId);
        environmentLabel = orUnknown(environmentLabel);
    }

    public static EvaluationBuildMetadata of(String gitSha, String buildId) {
        return of(gitSha, buildId, UNKNOWN);
    }

    public static EvaluationBuildMetadata of(String gitSha, String buildId, String environmentLabel) {
        return new EvaluationBuildMetadata(gitSha, buildId, environmentLabel);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(EvaluationProvenanceKeys.GIT_SHA, gitSha);
        out.put(EvaluationProvenanceKeys.BUILD_ID, buildId);
        out.put(EvaluationProvenanceKeys.ENVIRONMENT_LABEL, environmentLabel);
        return Map.copyOf(out);
    }

    private static String orUnknown(String value) {
        return value != null && !value.isBlank() ? value.trim() : UNKNOWN;
    }
}
