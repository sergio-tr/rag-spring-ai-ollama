package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.factual.FinalAnswerSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FactualVerifierTelemetry {

    public static final String STAGE_VERIFY = "factual_verify";
    public static final String STAGE_VERIFY_FINAL = "factual_verify_final";
    public static final String STAGE_VERIFY_REVISION = "factual_verify_revision";

    public static final String STAGE_VERIFY_SKIPPED = "factual_verify_skipped";

    private FactualVerifierTelemetry() {}

    public static String formatSkippedMessage(String reason) {
        String safeReason = reason != null ? reason : "skipped";
        return "verifierAttempted=false verifierPassed=false verifierFailureReason="
                + safeReason
                + " verifierRevisionAttempted=false verifierForcedAbstention=false constraintCheckPassed=false";
    }

    public static String formatMessage(
            FactualVerifierResult result, boolean revisionAttempted, boolean forcedAbstention) {
        String failures = result != null ? String.join(",", result.failures().stream().map(Enum::name).toList()) : "";
        boolean passed = result != null && result.passed();
        return "verifierAttempted=true"
                + " verifierPassed=" + passed
                + " verifierFailureReason=" + (result != null ? result.primaryFailureCode() : "")
                + " verifierFailures=" + failures
                + " verifierRevisionAttempted=" + revisionAttempted
                + " verifierForcedAbstention=" + forcedAbstention
                + " constraintCheckPassed=" + passed;
    }

    public static void enrichFromStages(List<ExecutionStageTrace> stages, Map<String, Object> target) {
        if (stages == null || target == null) {
            return;
        }
        boolean attempted = false;
        boolean passed = false;
        boolean revisionAttempted = false;
        boolean forcedAbstention = false;
        boolean explicitlySkipped = false;
        String failureReason = "";
        for (ExecutionStageTrace stage : stages) {
            if (stage == null || stage.message() == null) {
                continue;
            }
            if (STAGE_VERIFY_SKIPPED.equals(stage.stageName())) {
                explicitlySkipped = true;
                Map<String, String> tokens = parseTokens(stage.message());
                if (tokens.containsKey("verifierFailureReason") && !tokens.get("verifierFailureReason").isBlank()) {
                    failureReason = tokens.get("verifierFailureReason");
                }
                continue;
            }
            if (STAGE_VERIFY_REVISION.equals(stage.stageName()) && stage.message().contains("attempted=true")) {
                revisionAttempted = true;
            }
            if (!STAGE_VERIFY.equals(stage.stageName()) && !STAGE_VERIFY_FINAL.equals(stage.stageName())) {
                continue;
            }
            attempted = true;
            Map<String, String> tokens = parseTokens(stage.message());
            if (tokens.containsKey("verifierPassed")) {
                passed = "true".equalsIgnoreCase(tokens.get("verifierPassed"));
            }
            if (tokens.containsKey("verifierFailureReason") && !tokens.get("verifierFailureReason").isBlank()) {
                failureReason = tokens.get("verifierFailureReason");
            }
            if ("true".equalsIgnoreCase(tokens.get("verifierRevisionAttempted"))) {
                revisionAttempted = true;
            }
            if ("true".equalsIgnoreCase(tokens.get("verifierForcedAbstention"))) {
                forcedAbstention = true;
            }
        }
        if (explicitlySkipped) {
            target.put("verifierAttempted", false);
            target.put("verifierPassed", false);
            target.put("verifierRevisionAttempted", false);
            target.put("verifierForcedAbstention", false);
            target.put("constraintCheckPassed", false);
            if (!failureReason.isBlank()) {
                target.put("verifierFailureReason", failureReason);
            }
            return;
        }
        if (!attempted) {
            return;
        }
        target.put("verifierAttempted", true);
        target.put("verifierPassed", passed);
        if (!failureReason.isBlank()) {
            target.put("verifierFailureReason", failureReason);
        } else if (!passed) {
            target.put("verifierFailureReason", "verification_failed");
        }
        target.put("verifierRevisionAttempted", revisionAttempted);
        target.put("verifierForcedAbstention", forcedAbstention);
        target.put("constraintCheckPassed", passed);
    }

    public static void putPolicyFields(FactualQuestionConstraints constraints, Map<String, Object> target) {
        if (constraints == null || target == null) {
            return;
        }
        target.put("groundingPolicy", constraints.groundingPolicy().name());
        target.put("constraintType", constraints.constraintType().name());
        boolean negativeGuard =
                constraints.groundingPolicy() == com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy.NEGATIVE_EVIDENCE
                        || constraints.absenceQuestion();
        target.put("negativeEvidenceGuardTriggered", negativeGuard);
    }

    public static void putFinalAnswerSource(FinalAnswerSource source, Map<String, Object> target) {
        if (source != null && target != null) {
            target.put("finalAnswerSource", source.name());
        }
    }

    private static Map<String, String> parseTokens(String message) {
        Map<String, String> out = new LinkedHashMap<>();
        if (message == null) {
            return out;
        }
        for (String part : message.split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }
}
