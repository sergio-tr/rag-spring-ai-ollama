import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmbeddingEvaluatorOptionsForm } from "@/features/lab/components/EmbeddingEvaluatorOptionsForm";
import { buildEmbeddingBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-embedding-hyperparameters";
import { IntlTestProvider } from "@/test-utils/intl";
import type { LabEvaluationModelDto } from "@/types/api";

const models: LabEvaluationModelDto[] = [
  {
    modelName: "bge-m3",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "UNKNOWN",
    embeddingDimensions: 1024,
    compatibleWithCurrentVectorStore: true,
    usableAsDefault: true,
    supportsEncodingFormat: true,
    supportedEncodingFormats: ["float", "base64"],
    supportsDimensions: true,
    defaultDimensions: 1024,
    maxInputTokens: 8192,
    supportsNormalize: true,
    supportsTruncate: true,
  },
  {
    modelName: "mxbai-embed-large",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "UNKNOWN",
    embeddingDimensions: 1024,
    compatibleWithCurrentVectorStore: true,
    usableAsDefault: false,
    supportsEncodingFormat: true,
    supportedEncodingFormats: ["float", "base64"],
    supportsDimensions: false,
    defaultDimensions: 1024,
    maxInputTokens: 512,
    supportsNormalize: false,
    supportsTruncate: false,
  },
];

describe("EmbeddingEvaluatorOptionsForm", () => {
  it("omits dimensions in payload when unsupported by all selected models", () => {
    render(
      <IntlTestProvider>
        <EmbeddingEvaluatorOptionsForm
          value={{}}
          onChange={() => {}}
          selectedModels={[models[1]]}
        />
      </IntlTestProvider>,
    );

    const dimensions = screen.getByTestId("lab-emb-dimensions").querySelector("input");
    expect(dimensions).toBeDisabled();

    const payload = buildEmbeddingBenchmarkRuntimeParametersPayload({
      encodingFormat: "float",
      topK: 5,
      similarityThreshold: 0.4,
    });
    expect(payload?.embeddingOptions).toEqual({ encodingFormat: "float" });
    expect(payload?.topK).toBe(5);
    expect(payload?.similarityThreshold).toBe(0.4);
    expect((payload?.embeddingOptions as Record<string, unknown> | undefined)?.dimensions).toBeUndefined();
  });

  it("does not expose LLM generation controls", () => {
    render(
      <IntlTestProvider>
        <EmbeddingEvaluatorOptionsForm value={{}} onChange={() => {}} selectedModels={[...models]} />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("lab-hp-temperature")).not.toBeInTheDocument();
    expect(screen.getByTestId("embedding-evaluator-options-form")).toBeInTheDocument();
  });
});
