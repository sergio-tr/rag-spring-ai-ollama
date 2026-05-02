/**
 * UI-agnostic operational trace model for Phase 3 (tips / progress history).
 * Do not store secrets, tokens, raw prompts, PII, or stack traces in metadata or messages.
 */

export type TraceSection =
  | "projects"
  | "documents"
  | "chat"
  | "lab"
  | "settings"
  | "account"
  | "auth"
  | "global";

export type TraceStatus = "info" | "in_progress" | "success" | "warning" | "error";

export type TraceMetadataPrimitive = string | number | boolean;

/**
 * Bounded key/value bag for non-sensitive diagnostics only.
 * Values are primitives; nested objects are rejected at runtime sanitization.
 */
export type TraceMetadata = Readonly<Partial<Record<string, TraceMetadataPrimitive>>>;

export type TraceEvent = Readonly<{
  id: string;
  /** ISO 8601 UTC timestamp */
  timestamp: string;
  section: TraceSection;
  /** Short verb or code for what happened (e.g. "upload_started"). */
  action: string;
  message: string;
  status: TraceStatus;
  metadata?: TraceMetadata;
}>;

export type AddTraceEventInput = Readonly<{
  section: TraceSection;
  action: string;
  message: string;
  status: TraceStatus;
  metadata?: TraceMetadata;
}>;

/** Allowed updates after creation (immutable event identity: id + timestamp + section + action fixed). */
export type UpdateTraceEventPatch = Readonly<{
  message?: string;
  status?: TraceStatus;
  metadata?: TraceMetadata;
}>;
