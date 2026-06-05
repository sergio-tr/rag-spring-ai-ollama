/**
 * Stable W3C trace id for a Chat turn or Lab job follow session (POST + poll + SSE).
 */
import { createTraceparent } from "@/lib/traceparent";

let activeTraceparent: string | null = null;

/** Starts a new trace session; returns the traceparent value for outbound headers. */
export function beginTraceSession(): string {
  activeTraceparent = createTraceparent();
  return activeTraceparent;
}

/** Returns the active traceparent or creates one if missing. */
export function currentTraceparent(): string {
  if (!activeTraceparent) {
    return beginTraceSession();
  }
  return activeTraceparent;
}

/** Clears the session after a terminal Lab job or completed Chat turn. */
export function endTraceSession(): void {
  activeTraceparent = null;
}

/** Parses trace id (32 hex chars) from a W3C traceparent header. */
export function traceIdFromTraceparent(traceparent: string): string | null {
  const parts = traceparent.trim().split("-");
  if (parts.length !== 4) {
    return null;
  }
  const traceId = parts[1];
  return traceId.length === 32 ? traceId : null;
}
