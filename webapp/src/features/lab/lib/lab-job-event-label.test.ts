import { describe, expect, it } from "vitest";
import { formatLabJobEventLine } from "./lab-job-event-label";
import type { LabJobEventDto } from "@/types/api";

function event(partial: Partial<LabJobEventDto>): LabJobEventDto {
  return {
    eventId: 1,
    jobId: "j1",
    type: "ITEM_STARTED",
    status: "RUNNING",
    progress: null,
    message: null,
    timestamp: new Date().toISOString(),
    payload: null,
    ...partial,
  };
}

describe("formatLabJobEventLine", () => {
  it("prefers message with preset and model suffix", () => {
    const line = formatLabJobEventLine(
      event({
        message: "Item 2/5 started",
        currentPresetCode: "P7",
        currentModelId: "gemma3:4b",
      }),
    );
    expect(line).toContain("Item 2/5 started");
    expect(line).toContain("P7");
    expect(line).toContain("gemma3:4b");
  });

  it("falls back to run item counters", () => {
    const line = formatLabJobEventLine(
      event({ message: null, runCompletedItems: 3, runTotalItems: 10 }),
    );
    expect(line).toBe("Item 3/10");
  });
});
