import { describe, expect, it } from "vitest";
import { buildAssistantInstructionsPreview } from "./assistant-instructions-preview";

describe("assistant-instructions-preview", () => {
  it("truncates long previews for display", () => {
    const long = "x".repeat(400);
    const layers = buildAssistantInstructionsPreview({
      mode: "user",
      systemInstructions: long,
    });
    const preview = layers[0]?.preview ?? "";
    expect(preview.length).toBeLessThan(long.length);
    expect(preview.endsWith("…")).toBe(true);
  });

  it("marks project source layer not applicable in user mode", () => {
    const layers = buildAssistantInstructionsPreview({
      mode: "user",
      sourceUsageInstructions: "should be ignored",
    });
    expect(layers.find((l) => l.id === "sourceUsage")?.status).toBe("not_applicable");
  });
});
