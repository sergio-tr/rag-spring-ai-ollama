import { describe, expect, it } from "vitest";
import { mergeLabProgressSnapshot, EMPTY_LAB_PROGRESS_SNAPSHOT } from "./lab-job-progress-payload";
import type { LabJobEventDto } from "@/types/api";

describe("mergeLabProgressSnapshot", () => {
  it("keeps campaign totals when event buffer would drop early events", () => {
    const snap = mergeLabProgressSnapshot(EMPTY_LAB_PROGRESS_SNAPSHOT, {
      eventId: 1,
      jobId: "j1",
      type: "RUN_STARTED",
      status: "RUNNING",
      progress: null,
      message: "model-a · campaign 108 items",
      timestamp: "2026-01-01T00:00:00Z",
      payload: { totalItems: 108, currentItem: 0 },
      globalTotalItems: 108,
      globalCompletedItems: 0,
      runTotalItems: 36,
      runCompletedItems: 0,
      currentModelId: "model-a",
    } as LabJobEventDto);

    const later = mergeLabProgressSnapshot(snap, {
      eventId: 2,
      jobId: "j1",
      type: "ITEM_COMPLETED",
      status: "RUNNING",
      progress: null,
      message: "Completed · model-a · 5/108",
      timestamp: "2026-01-01T00:00:01Z",
      payload: { totalItems: 108, currentItem: 5, userMessage: "Completed · model-a · 5/108" },
      globalTotalItems: 108,
      globalCompletedItems: 5,
      runTotalItems: 36,
      runCompletedItems: 5,
      currentModelId: "model-a",
    } as LabJobEventDto);

    expect(later.globalTotal).toBe(108);
    expect(later.currentItem).toBe(5);
    expect(later.currentModelId).toBe("model-a");
  });
});
