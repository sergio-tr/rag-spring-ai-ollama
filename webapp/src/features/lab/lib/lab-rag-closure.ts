import type { AsyncTaskStatusDto } from "@/types/api";

export type BenchmarkClosureSummary = {
  expectedItems: number;
  executedItems: number;
  failedItems: number;
  skippedItems: number;
  notSupportedItems: number;
  classification: string | null;
};

const EMPTY_SUCCESS_FAILURE_CODES = new Set([
  "BENCHMARK_NO_EXECUTABLE_ITEMS",
  "BENCHMARK_ALL_ITEMS_SKIPPED",
  "COMPLETED_WITH_NO_EXECUTED_ITEMS",
  "FAILED_VALIDATION",
  "BENCHMARK_SKIPPED_WITHOUT_REASON",
  "BENCHMARK_NOT_SUPPORTED_WITHOUT_REASON",
]);

export function readBenchmarkClosureFromTask(
  taskStatus: AsyncTaskStatusDto | null,
): BenchmarkClosureSummary | null {
  const result = taskStatus?.result;
  if (!result || typeof result !== "object") {
    return null;
  }
  const closure = (result as Record<string, unknown>).benchmarkClosure;
  if (!closure || typeof closure !== "object") {
    return null;
  }
  const c = closure as Record<string, unknown>;
  return {
    expectedItems: numberOrZero(c.expectedItems),
    executedItems: numberOrZero(c.executedItems),
    failedItems: numberOrZero(c.failedItems),
    skippedItems: numberOrZero(c.skippedItems),
    notSupportedItems: numberOrZero(c.notSupportedItems),
    classification: typeof c.classification === "string" ? c.classification : null,
  };
}

/** Terminal success with at least one executed item (COMPLETED_OK / SUCCESS_WITH_RESULTS). */
export function isSuccessfulBenchmarkWithResults(taskStatus: AsyncTaskStatusDto | null): boolean {
  if (!taskStatus?.terminal) return false;
  if (taskStatusUpper(taskStatus.status) !== "SUCCEEDED") return false;
  if (isEmptyBenchmarkSuccess(taskStatus)) return false;
  const closure = readBenchmarkClosureFromTask(taskStatus);
  return Boolean(closure && closure.executedItems > 0);
}

function taskStatusUpper(status: string | null | undefined): string {
  return (status ?? "").trim().toUpperCase();
}

export function isEmptyBenchmarkSuccess(taskStatus: AsyncTaskStatusDto | null): boolean {
  if (!taskStatus?.terminal) {
    return false;
  }
  const code = taskStatus.failureCode?.trim();
  if (code && EMPTY_SUCCESS_FAILURE_CODES.has(code)) {
    return true;
  }
  const closure = readBenchmarkClosureFromTask(taskStatus);
  if (closure && closure.expectedItems > 0 && closure.executedItems <= 0) {
    return true;
  }
  const st = (taskStatus.status ?? "").trim().toUpperCase();
  if (st === "FAILED") {
    return false;
  }
  if (
    closure?.classification === RagBenchmarkClassification.COMPLETED_WITH_NO_EXECUTED_ITEMS ||
    closure?.classification === RagBenchmarkClassification.FAILED_VALIDATION
  ) {
    return true;
  }
  return false;
}

/** Mirrors backend {@link RagBenchmarkOutcomeTally} classification strings. */
export const RagBenchmarkClassification = {
  SUCCESS_WITH_RESULTS: "COMPLETED_OK",
  COMPLETED_OK: "COMPLETED_OK",
  COMPLETED_WITH_FAILURES: "COMPLETED_WITH_FAILURES",
  COMPLETED_WITH_UNSUPPORTED: "COMPLETED_WITH_UNSUPPORTED",
  COMPLETED_WITH_NO_EXECUTED_ITEMS: "COMPLETED_WITH_NO_EXECUTED_ITEMS",
  FAILED_VALIDATION: "FAILED_VALIDATION",
} as const;

export function closureClassificationLabel(
  classification: string | null,
  t: (key: string) => string,
): string | null {
  if (!classification) return null;
  switch (classification) {
    case RagBenchmarkClassification.COMPLETED_OK:
      return t("benchmarkClosureSuccessWithResults");
    case RagBenchmarkClassification.COMPLETED_WITH_FAILURES:
      return t("benchmarkClosureCompletedWithFailures");
    case RagBenchmarkClassification.COMPLETED_WITH_UNSUPPORTED:
      return t("benchmarkClosureCompletedWithUnsupported");
    case RagBenchmarkClassification.COMPLETED_WITH_NO_EXECUTED_ITEMS:
      return t("benchmarkClosureNoExecutedItems");
    case RagBenchmarkClassification.FAILED_VALIDATION:
      return t("benchmarkClosureFailedValidation");
    default:
      return null;
  }
}

export function readOutcomeCountsFromRollups(rollups: unknown): Record<string, number> {
  if (!rollups || typeof rollups !== "object") {
    return {};
  }
  const globalMacro = (rollups as Record<string, unknown>).globalMacro;
  if (!globalMacro || typeof globalMacro !== "object") {
    return {};
  }
  const oc = (globalMacro as Record<string, unknown>).outcomeCounts;
  if (!oc || typeof oc !== "object") {
    return {};
  }
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(oc as Record<string, unknown>)) {
    if (typeof v === "number" && Number.isFinite(v)) {
      out[k] = v;
    }
  }
  return out;
}

function numberOrZero(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
