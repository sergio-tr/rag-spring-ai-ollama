import { describe, it, expect } from "vitest";
import { resolveComparisonRowLabel } from "@/features/lab/lib/lab-benchmark-labels";

describe("resolveComparisonRowLabel embedding axis", () => {
  it("prefers embedding model axis value over downstream llm model label", () => {
    const label = resolveComparisonRowLabel(
      {
        axisValue: "bge-m3",
        embeddingModelId: "bge-m3",
        modelLabel: "gemma4:12b",
      },
      "EMBEDDING_MODEL",
    );
    expect(label).toBe("bge-m3");
  });
});
