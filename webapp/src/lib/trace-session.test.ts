import { afterEach, describe, expect, it } from "vitest";
import {
  beginTraceSession,
  currentTraceparent,
  endTraceSession,
  traceIdFromTraceparent,
} from "./trace-session";

describe("trace-session", () => {
  afterEach(() => {
    endTraceSession();
  });

  it("reuses the same traceparent across follow calls in one session", () => {
    const first = beginTraceSession();
    const second = currentTraceparent();
    const third = currentTraceparent();

    expect(second).toBe(first);
    expect(third).toBe(first);
    expect(traceIdFromTraceparent(first)).toHaveLength(32);
  });

  it("starts a new trace after endTraceSession", () => {
    const before = beginTraceSession();
    endTraceSession();
    const after = beginTraceSession();

    expect(after).not.toBe(before);
  });

  it("currentTraceparent lazily starts a session", () => {
    endTraceSession();
    const tp = currentTraceparent();
    expect(tp).toBeTruthy();
    expect(currentTraceparent()).toBe(tp);
  });

  it("traceIdFromTraceparent rejects malformed values", () => {
    expect(traceIdFromTraceparent("00-abc")).toBeNull();
    expect(traceIdFromTraceparent("00-1234-5678-01")).toBeNull();
    expect(traceIdFromTraceparent("00-12345678901234567890123456789012-1234567890123456-01")).toBe(
      "12345678901234567890123456789012",
    );
  });
});
