import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabModelConfigurationSection } from "./lab-model-configuration-section";

describe("LabModelConfigurationSection", () => {
  it("renders embedding selector only for RAG model configuration", async () => {
    const user = userEvent.setup();
    const onEmbeddingChange = vi.fn();

    render(
      <IntlTestProvider locale="en">
        <LabModelConfigurationSection
          sectionKey="evaluation-rag"
          embeddingModelId="bge-m3"
          embeddingModelIds={["bge-m3", "snowflake-arctic-embed2"]}
          selectedEmbeddingLabel="bge-m3"
          onEmbeddingChange={onEmbeddingChange}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByText("Model configuration")).toBeInTheDocument();
    expect(screen.getByText(/task-level LLM settings configured in Settings/i)).toBeInTheDocument();
    expect(screen.getByTestId("lab-benchmark-embedding-model")).toHaveValue("bge-m3");
    expect(screen.queryByTestId("lab-benchmark-llm-model")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-benchmark-secondary-llm-model")).not.toBeInTheDocument();
    expect(screen.queryByText("Primary model snapshot / campaign label")).not.toBeInTheDocument();
    expect(screen.queryByText(/campaign label/i)).not.toBeInTheDocument();

    await user.selectOptions(screen.getByTestId("lab-benchmark-embedding-model"), "snowflake-arctic-embed2");
    expect(onEmbeddingChange).toHaveBeenCalledWith("snowflake-arctic-embed2");
  });
});
