import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabHyperparametersForm } from "@/features/lab/components/lab-hyperparameters-form";
import { buildBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-generation-hyperparameters";

describe("LabHyperparametersForm", () => {
  it("embedding retrieval shows embedding evaluator options", () => {
    render(
      <IntlTestProvider>
        <LabHyperparametersForm
          benchmarkKind="EMBEDDING_RETRIEVAL"
          value={{ topK: 5, similarityThreshold: 0.25 }}
          onChange={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByText("Embedding evaluation parameters")).toBeInTheDocument();
    expect(screen.queryByTestId("lab-hp-embedding-model")).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-hp-top-k")).toBeInTheDocument();
    expect(screen.getByTestId("lab-hp-similarity-threshold")).toBeInTheDocument();
    expect(screen.getByTestId("embedding-evaluator-options-form")).toBeInTheDocument();
  });

});

describe("buildBenchmarkRuntimeParametersPayload", () => {
  const fullGeneration = {
    temperature: 0.1,
    topP: 1,
    seed: 42,
    maxTokens: 768,
    presencePenalty: 0,
    frequencyPenalty: 0,
    responseFormat: "json_object" as const,
    stop: ["END", "STOP"],
    think: false,
  };

  it("defaults think to omitted and serializes false when unset", () => {
    expect(buildBenchmarkRuntimeParametersPayload("LLM_JUDGE_QA", {})).toBeUndefined();
    expect(buildBenchmarkRuntimeParametersPayload("LLM_JUDGE_QA", { think: false })).toBeUndefined();
  });

  it("includes think only when enabled", () => {
    expect(buildBenchmarkRuntimeParametersPayload("LLM_JUDGE_QA", { think: true })).toEqual({ think: true });
  });

  it("LLM run request includes all generation params with snake_case API keys", () => {
    expect(buildBenchmarkRuntimeParametersPayload("LLM_JUDGE_QA", fullGeneration)).toEqual({
      temperature: 0.1,
      top_p: 1,
      seed: 42,
      max_tokens: 768,
      presence_penalty: 0,
      frequency_penalty: 0,
      response_format: { type: "json_object" },
      stop: ["END", "STOP"],
    });
  });

  it("RAG run request includes generation params for the answer LLM", () => {
    expect(
      buildBenchmarkRuntimeParametersPayload("RAG_PRESET_END_TO_END", {
        ...fullGeneration,
        topK: 8,
        similarityThreshold: 0.7,
        secondaryLlmModelId: "judge-model",
      }),
    ).toEqual({
      temperature: 0.1,
      top_p: 1,
      seed: 42,
      max_tokens: 768,
      presence_penalty: 0,
      frequency_penalty: 0,
      response_format: { type: "json_object" },
      stop: ["END", "STOP"],
      topK: 8,
      similarityThreshold: 0.7,
      secondaryLlmModelId: "judge-model",
    });
  });

  it("embedding run request does not include generation params", () => {
    expect(
      buildBenchmarkRuntimeParametersPayload("EMBEDDING_RETRIEVAL", {
        ...fullGeneration,
        topK: 12,
        similarityThreshold: 0.55,
      }),
    ).toEqual({
      topK: 12,
      similarityThreshold: 0.55,
    });
  });

  it("omits text response format from payload", () => {
    expect(
      buildBenchmarkRuntimeParametersPayload("LLM_JUDGE_QA", {
        temperature: 0.2,
        responseFormat: "text",
      }),
    ).toEqual({ temperature: 0.2 });
  });
});
