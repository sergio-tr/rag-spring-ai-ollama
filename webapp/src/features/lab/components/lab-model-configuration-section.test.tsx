import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabModelConfigurationSection } from "./lab-model-configuration-section";

describe("LabModelConfigurationSection", () => {
  it("renders editable embedding and chat model selectors", async () => {
    const user = userEvent.setup();
    const onEmbeddingChange = vi.fn();
    const onPrimaryLlmChange = vi.fn();

    render(
      <IntlTestProvider locale="en">
        <LabModelConfigurationSection
          sectionKey="evaluation-rag"
          embeddingModelId="bge-m3"
          primaryLlmModelId="gemma4:12b"
          secondaryLlmModelId="qwen3.5:9b"
          embeddingModelIds={["bge-m3", "snowflake-arctic-embed2"]}
          chatModelIds={["gemma4:12b", "qwen3.5:9b"]}
          selectedEmbeddingLabel="bge-m3"
          onEmbeddingChange={onEmbeddingChange}
          onPrimaryLlmChange={onPrimaryLlmChange}
          onSecondaryLlmChange={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByText("Model configuration")).toBeInTheDocument();
    expect(screen.getByTestId("lab-benchmark-embedding-model")).toHaveValue("bge-m3");
    expect(screen.getByTestId("lab-benchmark-llm-model")).toHaveValue("gemma4:12b");
    expect(screen.getByTestId("lab-benchmark-secondary-llm-model")).toHaveValue("qwen3.5:9b");

    await user.selectOptions(screen.getByTestId("lab-benchmark-embedding-model"), "snowflake-arctic-embed2");
    expect(onEmbeddingChange).toHaveBeenCalledWith("snowflake-arctic-embed2");

    await user.selectOptions(screen.getByTestId("lab-benchmark-llm-model"), "qwen3.5:9b");
    expect(onPrimaryLlmChange).toHaveBeenCalledWith("qwen3.5:9b");
  });
});
