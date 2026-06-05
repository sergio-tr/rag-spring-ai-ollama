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
});
