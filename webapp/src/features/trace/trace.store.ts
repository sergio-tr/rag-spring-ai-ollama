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
 * Not persisted — avoids leaking operational noise or sensitive text across sessions.
 */
export const MAX_TRACE_EVENTS = 100;

function newTraceId(): string {
  if (typeof globalThis.crypto !== "undefined" && typeof globalThis.crypto.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `trace-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
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
  const next: TraceEvent = {
    ...event,
    ...(patch.message !== undefined ? { message: patch.message } : {}),
    ...(patch.status !== undefined ? { status: patch.status } : {}),
  };
  if (patch.metadata === undefined) {
    return next;
  }
  const sanitized = sanitizeTraceMetadata(patch.metadata);
  if (sanitized !== undefined) {
    return { ...next, metadata: sanitized };
  }
  const { metadata: _removed, ...rest } = next;
  void _removed;
  return rest as TraceEvent;
}

export const useTraceStore = create<TraceStore>((set, get) => ({
  events: [],

  addTraceEvent: (input) => {
    const id = newTraceId();
    const timestamp = new Date().toISOString();
    const metadata = sanitizeTraceMetadata(input.metadata);
    const event: TraceEvent = {
      id,
      timestamp,
      section: input.section,
      action: input.action,
      message: input.message,
      status: input.status,
      ...(metadata !== undefined ? { metadata } : {}),
    };
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
