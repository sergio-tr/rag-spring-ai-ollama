import { describe, expect, it } from "vitest";
import {
  closureClassificationLabel,
  isEmptyBenchmarkSuccess,
  isSuccessfulBenchmarkWithResults,
  RagBenchmarkClassification,
  readBenchmarkClosureFromTask,
  readOutcomeCountsFromRollups,
} from "./lab-rag-closure";
import type { AsyncTaskStatusDto } from "@/types/api";

function task(partial: Partial<AsyncTaskStatusDto>): AsyncTaskStatusDto {
  return {
    id: "t1",
    taskType: "EVAL_RAG",
    status: "SUCCEEDED",
    terminal: true,
    progressText: null,
    errorMessage: null,
    result: null,
    createdAt: "",
    updatedAt: "",
    startedAt: null,
    completedAt: null,
    ...partial,
  };
}

describe("lab-rag-closure", () => {
  it("detects empty success from closure", () => {
    const taskStatus = task({
      result: {
        benchmarkClosure: {
          expectedItems: 60,
          executedItems: 0,
          skippedItems: 60,
          classification: "COMPLETED_WITH_NO_EXECUTED_ITEMS",
        },
      },
    });
    expect(isEmptyBenchmarkSuccess(taskStatus)).toBe(true);
  });

  it("allows success with executed items", () => {
    const taskStatus = task({
      result: {
        benchmarkClosure: {
          expectedItems: 60,
          executedItems: 50,
          failedItems: 10,
          classification: "COMPLETED_WITH_FAILURES",
        },
      },
    });
    expect(isEmptyBenchmarkSuccess(taskStatus)).toBe(false);
  });

  it("detects honest success with executed items", () => {
    const taskStatus = task({
      result: {
        benchmarkClosure: {
          expectedItems: 60,
          executedItems: 60,
          classification: "COMPLETED_OK",
        },
      },
    });
    expect(isSuccessfulBenchmarkWithResults(taskStatus)).toBe(true);
    expect(isEmptyBenchmarkSuccess(taskStatus)).toBe(false);
  });

  it("maps closure classification labels", () => {
    const t = (key: string) => key;
    expect(closureClassificationLabel("COMPLETED_OK", t)).toBe("benchmarkClosureSuccessWithResults");
  });

  it("reads classification from closure", () => {
    const taskStatus = task({
      result: {
        benchmarkClosure: {
          expectedItems: 60,
          executedItems: 50,
          failedItems: 10,
          classification: "COMPLETED_WITH_FAILURES",
        },
      },
    });
    expect(readBenchmarkClosureFromTask(taskStatus)?.classification).toBe("COMPLETED_WITH_FAILURES");
  });

  it("readBenchmarkClosureFromTask returns null for invalid payloads", () => {
    expect(readBenchmarkClosureFromTask(null)).toBeNull();
    expect(readBenchmarkClosureFromTask(task({ result: null }))).toBeNull();
    expect(readBenchmarkClosureFromTask(task({ result: { other: true } }))).toBeNull();
    expect(readBenchmarkClosureFromTask(task({ result: { benchmarkClosure: "x" } }))).toBeNull();
  });

  it("readBenchmarkClosureFromTask coerces non-numeric counts to zero", () => {
    const summary = readBenchmarkClosureFromTask(
      task({
        result: {
          benchmarkClosure: {
            expectedItems: "n/a",
            executedItems: NaN,
            classification: 42,
          },
        },
      }),
    );
    expect(summary).toEqual({
      expectedItems: 0,
      executedItems: 0,
      failedItems: 0,
      skippedItems: 0,
      notSupportedItems: 0,
      classification: null,
    });
  });

  it("isEmptyBenchmarkSuccess handles failure codes and terminal edge cases", () => {
    expect(isEmptyBenchmarkSuccess(task({ terminal: false }))).toBe(false);
    expect(isEmptyBenchmarkSuccess(task({ terminal: true, status: "FAILED" }))).toBe(false);
    expect(
      isEmptyBenchmarkSuccess(
        task({ terminal: true, failureCode: "BENCHMARK_NO_EXECUTABLE_ITEMS" }),
      ),
    ).toBe(true);
    expect(
      isEmptyBenchmarkSuccess(task({ terminal: true, failureCode: "BENCHMARK_SKIPPED_WITHOUT_REASON" })),
    ).toBe(true);
    expect(
      isEmptyBenchmarkSuccess(task({ terminal: true, failureCode: "COMPLETED_WITH_NO_EXECUTED_ITEMS" })),
    ).toBe(true);
    expect(
      isEmptyBenchmarkSuccess(
        task({
          terminal: true,
          status: "SUCCEEDED",
          result: {
            benchmarkClosure: {
              expectedItems: 10,
              executedItems: 0,
              classification: RagBenchmarkClassification.FAILED_VALIDATION,
            },
          },
        }),
      ),
    ).toBe(true);
    expect(
      isEmptyBenchmarkSuccess(
        task({
          terminal: true,
          status: "SUCCEEDED",
          result: {
            benchmarkClosure: {
              classification: RagBenchmarkClassification.COMPLETED_WITH_NO_EXECUTED_ITEMS,
            },
          },
        }),
      ),
    ).toBe(true);
  });

  it("isSuccessfulBenchmarkWithResults rejects non-success terminals", () => {
    expect(isSuccessfulBenchmarkWithResults(task({ terminal: false }))).toBe(false);
    expect(isSuccessfulBenchmarkWithResults(task({ terminal: true, status: "FAILED" }))).toBe(false);
    expect(
      isSuccessfulBenchmarkWithResults(
        task({
          terminal: true,
          failureCode: "BENCHMARK_ALL_ITEMS_SKIPPED",
          result: {
            benchmarkClosure: { expectedItems: 1, executedItems: 0, classification: "x" },
          },
        }),
      ),
    ).toBe(false);
  });

  it("maps all closure classification labels and unknown values", () => {
    const t = (key: string) => key;
    expect(closureClassificationLabel(null, t)).toBeNull();
    expect(closureClassificationLabel(RagBenchmarkClassification.COMPLETED_WITH_FAILURES, t)).toBe(
      "benchmarkClosureCompletedWithFailures",
    );
    expect(closureClassificationLabel(RagBenchmarkClassification.COMPLETED_WITH_UNSUPPORTED, t)).toBe(
      "benchmarkClosureCompletedWithUnsupported",
    );
    expect(closureClassificationLabel(RagBenchmarkClassification.COMPLETED_WITH_NO_EXECUTED_ITEMS, t)).toBe(
      "benchmarkClosureNoExecutedItems",
    );
    expect(closureClassificationLabel(RagBenchmarkClassification.FAILED_VALIDATION, t)).toBe(
      "benchmarkClosureFailedValidation",
    );
    expect(closureClassificationLabel("UNKNOWN_CLASSIFICATION", t)).toBeNull();
  });

  it("readOutcomeCountsFromRollups ignores invalid rollups", () => {
    expect(readOutcomeCountsFromRollups(null)).toEqual({});
    expect(readOutcomeCountsFromRollups({ globalMacro: null })).toEqual({});
    expect(readOutcomeCountsFromRollups({ globalMacro: { outcomeCounts: null } })).toEqual({});
    expect(
      readOutcomeCountsFromRollups({
        globalMacro: { outcomeCounts: { ok: 3, bad: "x", nan: Number.NaN } },
      }),
    ).toEqual({ ok: 3 });
  });
});
