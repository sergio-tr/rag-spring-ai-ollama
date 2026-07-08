import { create } from "zustand";
import type {
  AddTraceEventInput,
  TraceEvent,
  TraceSection,
  UpdateTraceEventPatch,
} from "./trace-types";
import { sanitizeTraceMetadata } from "./trace-metadata";

/**
 * In-memory ring buffer cap for trace events (oldest dropped after add).
 * Not persisted - avoids leaking operational noise or sensitive text across sessions.
 */
export const MAX_TRACE_EVENTS = 100;

/** Monotonic suffix only when Web Crypto is unavailable (e.g. some test mocks). Not used for secrets. */
let traceIdSeqFallback = 0;

function randomHexTraceSuffix(cryptoObj: Crypto, lengthBytes: number): string {
  const bytes = new Uint8Array(lengthBytes);
  cryptoObj.getRandomValues(bytes);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}

/**
 * Produces an unpredictable trace row id (in-memory UI only, not an auth or persistence credential).
 * Preferring `randomUUID()`, then `getRandomValues()` (CSPRNG); avoids `Math.random()` (Sonar S2245).
 */
function newTraceId(): string {
  const cryptoObj = globalThis.crypto;
  if (cryptoObj?.randomUUID !== undefined) {
    return cryptoObj.randomUUID();
  }
  if (cryptoObj?.getRandomValues !== undefined) {
    return `trace-${randomHexTraceSuffix(cryptoObj, 16)}`;
  }
  traceIdSeqFallback += 1;
  return `trace-${Date.now()}-${traceIdSeqFallback}`;
}

type TraceStore = {
  readonly events: readonly TraceEvent[];
  /** Appends an event; returns the new event id. */
  addTraceEvent: (input: AddTraceEventInput) => string;
  /** Updates message/status/metadata for an existing id. Returns false if id missing. */
  updateTraceEvent: (id: string, patch: UpdateTraceEventPatch) => boolean;
  clearTraceEvents: () => void;
  clearTraceEventsBySection: (section: TraceSection) => void;
};

function applyTracePatch(event: TraceEvent, patch: UpdateTraceEventPatch): TraceEvent {
  let next: TraceEvent = { ...event };
  if (patch.message !== undefined) {
    next = { ...next, message: patch.message };
  }
  if (patch.status !== undefined) {
    next = { ...next, status: patch.status };
  }
  if (patch.metadata === undefined) {
    return next;
  }
  const sanitized = sanitizeTraceMetadata(patch.metadata);
  if (sanitized !== undefined) {
    return { ...next, metadata: sanitized };
  }
  const rest = { ...next };
  delete rest.metadata;
  return rest;
}

export const useTraceStore = create<TraceStore>((set, get) => ({
  events: [],

  addTraceEvent: (input) => {
    const id = newTraceId();
    const timestamp = new Date().toISOString();
    const metadata = sanitizeTraceMetadata(input.metadata);
    const baseEvent = {
      id,
      timestamp,
      section: input.section,
      action: input.action,
      message: input.message,
      status: input.status,
    };
    const event: TraceEvent =
      metadata === undefined ? baseEvent : { ...baseEvent, metadata };
    set((s) => ({
      events: [...s.events, event].slice(-MAX_TRACE_EVENTS),
    }));
    return id;
  },

  updateTraceEvent: (id, patch) => {
    const events = get().events;
    const index = events.findIndex((e) => e.id === id);
    if (index === -1) return false;

    set(() => ({
      events: events.map((e, i) => (i === index ? applyTracePatch(e, patch) : e)),
    }));
    return true;
  },

  clearTraceEvents: () => set({ events: [] }),

  clearTraceEventsBySection: (section) =>
    set((s) => ({
      events: s.events.filter((e) => e.section !== section),
    })),
}));
