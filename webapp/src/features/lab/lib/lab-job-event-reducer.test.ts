import { describe, expect, it } from "vitest";
import { reduceLabJobEvents, progressPercent } from "./lab-job-event-reducer";
import type { LabJobEventDto } from "@/types/api";

function ev(partial: Partial<LabJobEventDto> & Pick<LabJobEventDto, "type" | "eventId">): LabJobEventDto {
  return {
    jobId: "job-1",
    status: "RUNNING",
    progress: null,
    message: null,
    timestamp: "2026-01-01T00:00:00Z",
    payload: null,
    ...partial,
  };
}

describe("reduceLabJobEvents", () => {
  it("dedupes phase milestones and tracks item progress", () => {
    const view = reduceLabJobEvents([
      ev({
        eventId: 1,
        type: "DATASET_RESOLVED",
        message: "Dataset ready · 3 questions",
        payload: { phase: "DATASET" },
      }),
      ev({
        eventId: 2,
        type: "DATASET_RESOLVED",
        message: "Dataset ready · 3 questions",
        payload: { phase: "DATASET" },
      }),
      ev({
        eventId: 3,
        type: "ITEM_COMPLETED",
        message: "P2 · Question 1/3",
        runCompletedItems: 1,
        runTotalItems: 3,
        currentPresetCode: "P2",
        payload: { phase: "RAG_EVALUATION" },
      }),
    ]);
    expect(view.phase).toBe("RUNNING");
    expect(view.itemsCompleted).toBe(1);
    expect(view.presetCode).toBe("P2");
    expect(view.subtasks.filter((s) => s.type === "DATASET_RESOLVED")).toHaveLength(1);
    expect(progressPercent(view)).toBe(33);
  });

  it("prefers global campaign counters over per-run totals", () => {
    const view = reduceLabJobEvents([
      ev({
        eventId: 1,
        type: "RUN_STARTED",
        message: "Campaign started",
        globalTotalItems: 108,
        runTotalItems: 36,
      }),
      ev({
        eventId: 2,
        type: "ITEM_COMPLETED",
        message: "Model A · Question 5/36",
        globalCompletedItems: 5,
        globalTotalItems: 108,
        runCompletedItems: 5,
        runTotalItems: 36,
      }),
    ]);
    expect(progressPercent(view)).toBe(Math.round((5 / 108) * 100));
    expect(view.globalCompleted).toBe(5);
    expect(view.globalTotal).toBe(108);
    expect(view.currentItem).toBe(5);
    expect(view.totalItems).toBe(108);
  });

  it("routes spammy progress lines to technical only", () => {
    const view = reduceLabJobEvents([
      ev({ eventId: 1, type: "PROGRESS", message: "Resolving typed dataset for RAG…" }),
      ev({ eventId: 2, type: "KNOWLEDGE_BASE_CHECKED", message: "Knowledge base ready · 1/1 documents" }),
    ]);
    expect(view.subtasks).toHaveLength(1);
    expect(view.technicalEvents).toHaveLength(1);
    expect(view.phase).toBe("KNOWLEDGE_BASE");
  });

  it("tracks failed, skipped, export, and terminal phases", () => {
    const view = reduceLabJobEvents([
      ev({ eventId: 1, type: "CAMPAIGN_PLANNED", message: "Planned 3 presets" }),
      ev({ eventId: 2, type: "SNAPSHOT_PREPARATION_STARTED", message: "Indexing…" }),
      ev({ eventId: 3, type: "ITEM_FAILED", message: "Item failed" }),
      ev({ eventId: 4, type: "ITEM_SKIPPED", message: "Item skipped" }),
      ev({ eventId: 5, type: "EXPORT_GENERATED", message: "Export ready" }),
      ev({ eventId: 6, type: "FAILED", message: "Campaign failed" }),
    ]);
    expect(view.phase).toBe("FAILED");
    expect(view.itemsFailed).toBe(1);
    expect(view.itemsSkipped).toBe(1);
    expect(view.subtasks.some((s) => s.status === "failed")).toBe(true);
  });

  it("ignores heartbeat and snapshot noise", () => {
    const view = reduceLabJobEvents([
      ev({ eventId: 1, type: "HEARTBEAT", message: "ping" }),
      ev({ eventId: 2, type: "SNAPSHOT", message: "snap" }),
      ev({ eventId: 3, type: "COMPLETED", message: "Done" }),
    ]);
    expect(view.phase).toBe("COMPLETED");
    expect(view.subtasks).toHaveLength(1);
  });

  it("uses payload phase, labels, and model metadata", () => {
    const view = reduceLabJobEvents([
      ev({
        eventId: 1,
        type: "SNAPSHOT_PREPARATION_STARTED",
        message: "Indexing snapshot",
        payload: { phase: "INDEXING", label: "Custom label" },
        currentPresetCode: " P2 ",
        currentModelId: " llama ",
      }),
    ]);
    expect(view.phase).toBe("INDEXING");
    expect(view.presetCode).toBe("P2");
    expect(view.currentModelId).toBe("llama");
    expect(view.subtasks[0]?.label).toBe("Custom label");
  });

  it("progressPercent returns null when totals are missing", () => {
    expect(progressPercent(reduceLabJobEvents([]))).toBeNull();
    expect(
      progressPercent({
        phase: "RUNNING",
        phaseLabel: null,
        presetCode: null,
        currentModelId: null,
        currentItem: 1,
        totalItems: 0,
        globalCompleted: null,
        globalTotal: null,
        itemsCompleted: 0,
        itemsFailed: 0,
        itemsSkipped: 0,
        lastAction: null,
        subtasks: [],
        technicalEvents: [],
      }),
    ).toBeNull();
  });
});
