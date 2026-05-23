import { describe, expect, it, vi } from "vitest";
import { adminModelCheckSummary, adminModelUserMessage } from "./admin-model-errors";

describe("admin-model-errors", () => {
  const t = vi.fn((key: string) => key);

  it("maps embedding probe failure codes to friendly copy", () => {
    expect(adminModelUserMessage("MODEL_EMBEDDING_PROBE_FAILED", t)).toBe("probeEmbeddingFailed");
    expect(adminModelUserMessage("MODEL_TYPE_MISMATCH", t)).toBe("probeEmbeddingFailed");
  });

  it("summarizes successful LLM check", () => {
    expect(
      adminModelCheckSummary(
        {
          existsLocal: true,
          embeddingProbeOk: true,
          requestedType: "LLM",
          errorCode: null,
        },
        t,
      ),
    ).toBe("probeOk");
  });
});
