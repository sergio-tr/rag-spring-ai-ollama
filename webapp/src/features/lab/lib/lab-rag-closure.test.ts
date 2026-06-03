import { describe, expect, it } from "vitest";
import {
  closureClassificationLabel,
  isEmptyBenchmarkSuccess,
  isSuccessfulBenchmarkWithResults,
  readBenchmarkClosureFromTask,
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
});
