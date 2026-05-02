import { describe, it, expect, beforeEach, vi } from "vitest";
import { MAX_TRACE_EVENTS, useTraceStore } from "./trace.store";
import type { TraceSection, TraceStatus } from "./trace-types";

describe("useTraceStore", () => {
  beforeEach(() => {
    useTraceStore.getState().clearTraceEvents();
  });

  it("addTraceEvent appends an event with id and ISO timestamp", () => {
    const id = useTraceStore.getState().addTraceEvent({
      section: "global",
      action: "boot",
      message: "ok",
      status: "info",
    });
    expect(id.length).toBeGreaterThan(0);
    const [ev] = useTraceStore.getState().events;
    expect(ev?.id).toBe(id);
    expect(ev?.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    expect(ev?.section).toBe("global");
    expect(ev?.action).toBe("boot");
    expect(ev?.message).toBe("ok");
    expect(ev?.status).toBe("info");
  });

  it("does not mutate prior event references when adding a second event", () => {
    useTraceStore.getState().addTraceEvent({
      section: "projects",
      action: "a",
      message: "m1",
      status: "success",
    });
    const firstBefore = useTraceStore.getState().events[0];
    expect(firstBefore).toBeDefined();
    useTraceStore.getState().addTraceEvent({
      section: "documents",
      action: "b",
      message: "m2",
      status: "info",
    });
    expect(useTraceStore.getState().events[0]).toBe(firstBefore);
  });

  it("drops oldest events when exceeding MAX_TRACE_EVENTS", () => {
    let n = 0;
    vi.spyOn(globalThis.crypto, "randomUUID").mockImplementation(() => `uuid-${++n}`);
    try {
      for (let i = 0; i < MAX_TRACE_EVENTS + 10; i += 1) {
        useTraceStore.getState().addTraceEvent({
          section: "chat",
          action: `t${i}`,
          message: "x",
          status: "info",
        });
      }
      const events = useTraceStore.getState().events;
      expect(events.length).toBe(MAX_TRACE_EVENTS);
      expect(events[0]?.action).toBe("t10");
      expect(events.at(-1)?.action).toBe(`t${MAX_TRACE_EVENTS + 9}`);
    } finally {
      vi.mocked(globalThis.crypto.randomUUID).mockRestore();
    }
  });

  it("clearTraceEvents removes all", () => {
    useTraceStore.getState().addTraceEvent({
      section: "lab",
      action: "run",
      message: "started",
      status: "in_progress",
    });
    useTraceStore.getState().clearTraceEvents();
    expect(useTraceStore.getState().events).toHaveLength(0);
  });

  it("clearTraceEventsBySection removes only that section", () => {
    const sections: TraceSection[] = ["settings", "chat", "settings"];
    for (const section of sections) {
      useTraceStore.getState().addTraceEvent({
        section,
        action: "x",
        message: "m",
        status: "warning",
      });
    }
    useTraceStore.getState().clearTraceEventsBySection("settings");
    const remaining = useTraceStore.getState().events;
    expect(remaining).toHaveLength(1);
    expect(remaining[0]?.section).toBe("chat");
  });

  it("updateTraceEvent patches message and status", () => {
    const id = useTraceStore.getState().addTraceEvent({
      section: "account",
      action: "export",
      message: "queued",
      status: "in_progress",
    });
    const ok = useTraceStore.getState().updateTraceEvent(id, {
      message: "done",
      status: "success",
    });
    expect(ok).toBe(true);
    const ev = useTraceStore.getState().events.find((e) => e.id === id);
    expect(ev?.message).toBe("done");
    expect(ev?.status).toBe("success");
    expect(ev?.action).toBe("export");
  });

  it("updateTraceEvent returns false for unknown id", () => {
    expect(useTraceStore.getState().updateTraceEvent("missing", { message: "x" })).toBe(false);
  });

  it("updateTraceEvent clears metadata when patch sanitizes to empty", () => {
    const id = useTraceStore.getState().addTraceEvent({
      section: "global",
      action: "x",
      message: "m",
      status: "info",
      metadata: { k: "v" },
    });
    expect(useTraceStore.getState().events[0]?.metadata).toBeDefined();
    useTraceStore.getState().updateTraceEvent(id, {
      metadata: { nested: { a: 1 } as unknown as string },
    });
    expect(useTraceStore.getState().events.find((e) => e.id === id)?.metadata).toBeUndefined();
  });

  it("stores sanitized metadata from addTraceEvent", () => {
    useTraceStore.getState().addTraceEvent({
      section: "auth",
      action: "sign_in",
      message: "ok",
      status: "success",
      metadata: { code: "200", correlationId: "abc" },
    });
    expect(useTraceStore.getState().events[0]?.metadata).toEqual({ code: "200", correlationId: "abc" });
  });

  it("covers representative sections and statuses through typings", () => {
    const matrix: ReadonlyArray<{ section: TraceSection; status: TraceStatus }> = [
      { section: "projects", status: "info" },
      { section: "documents", status: "in_progress" },
      { section: "chat", status: "success" },
      { section: "lab", status: "warning" },
      { section: "settings", status: "error" },
      { section: "account", status: "info" },
      { section: "auth", status: "success" },
      { section: "global", status: "info" },
    ];
    for (const { section, status } of matrix) {
      useTraceStore.getState().addTraceEvent({ section, action: "test", message: "m", status });
    }
    expect(useTraceStore.getState().events.length).toBe(matrix.length);
  });
});
