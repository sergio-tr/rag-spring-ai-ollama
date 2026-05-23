import { describe, expect, it, vi } from "vitest";
import { adminModelCheckSummary, adminModelUserMessage } from "./admin-model-errors";

describe("admin-model-errors", () => {
  const t = vi.fn((key: string) => key);

  it("maps all known admin model error codes", () => {
    expect(adminModelUserMessage("MODEL_NOT_FOUND", t)).toBe("probeNotInstalled");
    expect(adminModelUserMessage("MODEL_EMBEDDING_PROBE_FAILED", t)).toBe("probeEmbeddingFailed");
    expect(adminModelUserMessage("MODEL_TYPE_MISMATCH", t)).toBe("probeEmbeddingFailed");
    expect(adminModelUserMessage("MODEL_PULL_FAILED", t)).toBe("probePullFailed");
    expect(adminModelUserMessage("OLLAMA_UNAVAILABLE", t)).toBe("probeOllamaUnreachable");
    expect(adminModelUserMessage("UNKNOWN", t)).toBe("modelCheckError");
    expect(adminModelUserMessage(null, t)).toBe("modelCheckError");
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

  it("summarizes missing local model", () => {
    expect(
      adminModelCheckSummary(
        {
          existsLocal: false,
          embeddingProbeOk: false,
          requestedType: "EMBEDDING",
          errorCode: "MODEL_NOT_FOUND",
        },
        t,
      ),
    ).toBe("probeNotInstalled");
  });

  it("summarizes failed embedding probe", () => {
    expect(
      adminModelCheckSummary(
        {
          existsLocal: true,
          embeddingProbeOk: false,
          requestedType: "EMBEDDING",
          errorCode: "MODEL_EMBEDDING_PROBE_FAILED",
        },
        t,
      ),
    ).toBe("probeEmbeddingFailed");
  });
});
