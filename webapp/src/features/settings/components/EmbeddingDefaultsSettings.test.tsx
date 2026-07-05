import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
      retrievalOptions: { topK: 10, similarityThreshold: 0.35, materializationStrategy: "CHUNK_LEVEL" },
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
          supportedEncodingFormats: ["float", "base64"],
          supportsDimensions: true,
          supportsNormalize: true,
          supportsTruncate: false,
        },
        {
          modelName: "nomic-embed-text",
          evalSelectable: true,
          supportsEncodingFormat: true,
          supportedEncodingFormats: ["float"],
          supportsDimensions: false,
          supportsNormalize: false,
          supportsTruncate: false,
        },
      ],
    },
  }),
}));

function Harness(props: Readonly<{ defaultValues?: ConfigFormValues; config?: Record<string, unknown> }>) {
  const form = useForm<ConfigFormValues>({
    defaultValues: props.defaultValues ?? {
      embeddingModel: "bge-m3",
    },
  });
  return (
    <IntlTestProvider>
      <EmbeddingDefaultsSettings form={form} config={props.config} />
    </IntlTestProvider>
  );
}

describe("EmbeddingDefaultsSettings", () => {
  it("renders embedding defaults without LLM generation params", () => {
    render(<Harness />);
    expect(screen.getByTestId("embedding-defaults-settings")).toBeInTheDocument();
    expect(screen.getByTestId("embedding-default-embeddingEncodingFormat")).toBeInTheDocument();
    expect(screen.queryByLabelText(/temperature/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/top p/i)).not.toBeInTheDocument();
  });

  it("shows effective values in editable fields instead of server default label", () => {
    render(<Harness />);
    expect(screen.queryByLabelText(/^Materialization strategy$/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Server default")).not.toBeInTheDocument();
    expect(screen.getAllByText(/Inherited from system default/i).length).toBeGreaterThan(0);
  });

  it("disables normalize for unsupported embedding models", () => {
    render(
      <Harness
        defaultValues={{ embeddingModel: "nomic-embed-text" }}
        config={{ embeddingModel: "nomic-embed-text" }}
      />,
    );
    const normalize = screen
      .getByTestId("embedding-default-embeddingNormalize")
      .querySelector('input[type="checkbox"]');
    expect(normalize).toBeDisabled();
    expect(screen.getAllByText(/Not supported by the selected embedding model/i).length).toBeGreaterThan(0);
  });

  it("updates select, number, and checkbox fields through the form", async () => {
    const user = userEvent.setup();
    render(<Harness defaultValues={{ embeddingModel: "bge-m3", embeddingEncodingFormat: "base64" }} />);

    const encodingSelect = screen.getByLabelText(/encoding format/i);
    await user.selectOptions(encodingSelect, "float");
    expect(encodingSelect).toHaveValue("float");

    const batchSize = screen.getByLabelText(/indexing batch size/i);
    await user.tripleClick(batchSize);
    await user.keyboard("12");
    expect(batchSize).toHaveValue(12);

    const normalize = screen
      .getByTestId("embedding-default-embeddingNormalize")
      .querySelector('input[type="checkbox"]');
    if (!(normalize instanceof HTMLInputElement)) {
      throw new Error("Normalize checkbox not found");
    }
    await user.click(normalize);
    expect(normalize).toBeChecked();
  });

  it("shows false for inherited normalize when effective default is false", () => {
    render(<Harness config={{ embeddingNormalize: false }} />);
    expect(screen.getByTestId("embedding-default-embeddingNormalize")).toHaveTextContent("false");
  });
});
