import { describe, expect, it } from "vitest";
import { buildLabBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-benchmark-runtime-payload";

describe("buildLabBenchmarkRuntimeParametersPayload", () => {
  it("RAG merges generation and nested embedding groups without materialization override", () => {
    expect(
      buildLabBenchmarkRuntimeParametersPayload("RAG_PRESET_END_TO_END", {
        temperature: 0.2,
        topK: 8,
        similarityThreshold: 0.7,
        encodingFormat: "float",
        dimensions: 1024,
        timeoutSeconds: 30,
        batchSize: 16,
        normalize: true,
      }),
    ).toEqual({
      temperature: 0.2,
      topK: 8,
      similarityThreshold: 0.7,
      embeddingOptions: {
        encodingFormat: "float",
        dimensions: 1024,
        timeoutSeconds: 30,
      },
      retrievalOptions: {
        topK: 8,
        similarityThreshold: 0.7,
      },
      indexingOptions: {
        batchSize: 16,
        normalize: true,
      },
    });
  });

  it("embedding run excludes generation params", () => {
    expect(
      buildLabBenchmarkRuntimeParametersPayload("EMBEDDING_RETRIEVAL", {
        temperature: 0.9,
        topK: 5,
        dimensions: 768,
      }),
    ).toEqual({
      topK: 5,
      embeddingOptions: { dimensions: 768 },
      retrievalOptions: { topK: 5 },
    });
  });
});
