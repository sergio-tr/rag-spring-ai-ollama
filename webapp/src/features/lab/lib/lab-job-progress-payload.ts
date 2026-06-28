import type { LabJobEventDto } from "@/types/api";

export function payloadNumber(
  payload: Record<string, unknown> | null | undefined,
  key: string,
): number | null {
  const raw = payload?.[key];
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "string" && raw.trim()) {
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

export function payloadString(
  payload: Record<string, unknown> | null | undefined,
  key: string,
): string | null {
  const raw = payload?.[key];
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

/** Merges counters from a single SSE event into a durable snapshot (survives event-buffer truncation). */
export function mergeLabProgressSnapshot(
  prev: LabProgressSnapshot,
  event: LabJobEventDto,
): LabProgressSnapshot {
  const next: LabProgressSnapshot = { ...prev };
  const payload = event.payload ?? undefined;

  const payloadTotal = payloadNumber(payload, "totalItems");
  const payloadCurrent = payloadNumber(payload, "currentItem");
  const globalTotal = event.globalTotalItems ?? payloadTotal;
  const globalDone = event.globalCompletedItems ?? payloadCurrent;

  if (globalTotal != null && globalTotal > 0) {
    next.globalTotal = globalTotal;
    next.totalItems = globalTotal;
  }
  if (globalDone != null && globalDone >= 0) {
    next.globalCompleted = globalDone;
    next.currentItem = globalDone;
  } else if (payloadCurrent != null && payloadCurrent > 0) {
    next.currentItem = payloadCurrent;
  }

  if (event.runTotalItems != null && event.runTotalItems > 0) {
    next.runTotalItems = event.runTotalItems;
  }
  if (event.runCompletedItems != null && event.runCompletedItems > 0) {
    next.runCompletedItems = event.runCompletedItems;
  }

  const userMessage = payloadString(payload, "userMessage");
  if (userMessage) {
    next.lastUserMessage = userMessage;
  } else if (event.message?.trim()) {
    next.lastUserMessage = event.message.trim();
  }

  if (event.currentModelId?.trim()) {
    next.currentModelId = event.currentModelId.trim();
  } else {
    const modelId = payloadString(payload, "modelId");
    if (modelId) next.currentModelId = modelId;
  }

  if (event.currentPresetCode?.trim()) {
    next.currentPresetCode = event.currentPresetCode.trim();
  } else {
    const preset = payloadString(payload, "presetCode");
    if (preset) next.currentPresetCode = preset;
  }

  return next;
}

export type LabProgressSnapshot = {
  globalTotal: number | null;
  globalCompleted: number | null;
  currentItem: number | null;
  totalItems: number | null;
  runTotalItems: number | null;
  runCompletedItems: number | null;
  currentModelId: string | null;
  currentPresetCode: string | null;
  lastUserMessage: string | null;
};

export const EMPTY_LAB_PROGRESS_SNAPSHOT: LabProgressSnapshot = {
  globalTotal: null,
  globalCompleted: null,
  currentItem: null,
  totalItems: null,
  runTotalItems: null,
  runCompletedItems: null,
  currentModelId: null,
  currentPresetCode: null,
  lastUserMessage: null,
};
