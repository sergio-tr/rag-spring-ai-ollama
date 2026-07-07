import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmbeddingDefaultsSettings } from "@/features/settings/components/EmbeddingDefaultsSettings";
import { useForm } from "react-hook-form";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: () => ({
    data: {
      effectiveProvider: "OPENAI_COMPATIBLE",
      embeddingModel: "bge-m3",
      embeddingOptions: { encodingFormat: "float", dimensions: 1024, timeoutSeconds: 45 },
      retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK_LEVEL" },
      indexingOptions: { maxInputChars: 2048, batchSize: 16, normalize: false },
    },
  }),
}));

vi.mock("@/features/lab/hooks/use-lab-evaluation-models", () => ({
  useLabEvaluationModels: () => ({
    data: {
      models: [
        {
          modelName: "bge-m3",
          evalSelectable: true,
          supportsEncodingFormat: true,
          supportedEncodingFormats: ["float"],
          supportsDimensions: true,
          supportsNormalize: true,
          supportsTruncate: false,
        },
      ],
    },
  }),
}));

function Harness() {
  const form = useForm<ConfigFormValues>({ defaultValues: { embeddingModel: "bge-m3" } });
  return (
    <IntlTestProvider locale="en">
      <EmbeddingDefaultsSettings form={form} config={{}} />
    </IntlTestProvider>
  );
}

describe("SettingsRetrievalDefaults", () => {
  it("does not expose materialization strategy in embedding defaults settings", () => {
    render(<Harness />);
    expect(screen.queryByLabelText(/^Materialization strategy$/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("embedding-default-embeddingBatchSize")).toBeInTheDocument();
  });
});
