package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

/** Strict schema validation for function-call arguments. */
public final class FunctionCallSchemaValidator {

    private FunctionCallSchemaValidator() {}

    public static void validateOrThrow(String argumentsJson, DeterministicToolKind kind, QueryPlan plan) {
        FcToolArgumentParser.parseOrThrow(argumentsJson, kind, plan);
    }

    public static boolean isValid(String argumentsJson, DeterministicToolKind kind, QueryPlan plan) {
        try {
            validateOrThrow(argumentsJson, kind, plan);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String validationError(String argumentsJson, DeterministicToolKind kind, QueryPlan plan) {
        try {
            validateOrThrow(argumentsJson, kind, plan);
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage() != null ? e.getMessage() : "invalid_arguments";
        }
    }

    public record ValidationWithRepairResult(
            boolean valid,
            String argumentsJson,
            boolean repairAttempted,
            boolean repairSucceeded,
            String validationError) {}

    /** Validates JSON arguments and applies at most one deterministic repair when invalid. */
    public static ValidationWithRepairResult validateWithOptionalRepair(
            String argumentsJson, DeterministicToolKind kind, QueryPlan plan) {
        if (isValid(argumentsJson, kind, plan)) {
            return new ValidationWithRepairResult(true, argumentsJson, false, false, "");
        }
        String repaired = FunctionCallArgumentRepairer.repairOnce(argumentsJson, kind, plan);
        if (repaired == null) {
            return new ValidationWithRepairResult(
                    false, argumentsJson, true, false, validationError(argumentsJson, kind, plan));
        }
        if (isValid(repaired, kind, plan)) {
            return new ValidationWithRepairResult(true, repaired, true, true, "");
        }
        return new ValidationWithRepairResult(
                false, argumentsJson, true, false, validationError(repaired, kind, plan));
    }
}
