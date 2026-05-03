import { describe, expect, it, beforeEach } from "vitest";
import { useTraceStore } from "@/features/trace/trace.store";
import {
  createAccountExportTraceDedupe,
  emitAccountExportTraceForTick,
  traceAccountExportQueued,
} from "./account-export-trace";
import type { AsyncTaskStatusDto } from "@/types/api";

function dto(partial: Partial<AsyncTaskStatusDto>): AsyncTaskStatusDto {
  return {
    id: "j1",
    taskType: "ACCOUNT_EXPORT",
    status: "QUEUED",
    progressText: null,
    result: null,
    errorMessage: null,
    terminal: false,
    createdAt: "",
    updatedAt: "",
    startedAt: null,
    completedAt: null,
    ...partial,
  };
}

describe("account-export-trace", () => {
  beforeEach(() => {
    useTraceStore.getState().clearTraceEvents();
  });

  it("traceAccountExportQueued appends an account section row", () => {
    traceAccountExportQueued("jid", "hello");
    expect(useTraceStore.getState().events).toHaveLength(1);
    expect(useTraceStore.getState().events[0]?.section).toBe("account");
    expect(useTraceStore.getState().events[0]?.action).toBe("account_export_queued");
  });

  it("emitAccountExportTraceForTick dedupes running and terminal once", () => {
    const dedupe = createAccountExportTraceDedupe();
    const messages = {
      queued: "q",
      running: "r",
      completed: "c",
      failed: "f",
      cancelled: "x",
    };
    emitAccountExportTraceForTick(dedupe, dto({ status: "RUNNING" }), "jid", messages);
    emitAccountExportTraceForTick(dedupe, dto({ status: "RUNNING" }), "jid", messages);
    emitAccountExportTraceForTick(
      dedupe,
      dto({ status: "SUCCEEDED", terminal: true }),
      "jid",
      messages,
    );
    emitAccountExportTraceForTick(
      dedupe,
      dto({ status: "SUCCEEDED", terminal: true }),
      "jid",
      messages,
    );
    const actions = useTraceStore.getState().events.map((e) => e.action);
    expect(actions.filter((a) => a === "account_export_running")).toHaveLength(1);
    expect(actions.filter((a) => a === "account_export_completed")).toHaveLength(1);
  });
});
