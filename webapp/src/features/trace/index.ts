/**
 * Operational trace / tips-history foundation (Phase 3A).
 * UI batches consume {@link useTraceStore}; guidance copy stays separate from this operational log.
 */

export type {
  AddTraceEventInput,
  TraceEvent,
  TraceMetadata,
  TraceSection,
  TraceStatus,
  UpdateTraceEventPatch,
} from "./trace-types";
export { sanitizeTraceMetadata, TRACE_METADATA_MAX_KEYS } from "./trace-metadata";
export { MAX_TRACE_EVENTS, useTraceStore } from "./trace.store";
