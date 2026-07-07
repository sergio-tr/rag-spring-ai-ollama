import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import { LabRagIndexingMaterializationPlan } from "./lab-rag-indexing-materialization-plan";

function preset(
  code: string,
  indexRequirements: ExperimentalPresetCatalogItemDto["indexRequirements"],
  overrides: Partial<ExperimentalPresetCatalogItemDto> = {},
): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: code,
    code,
    family: "x",
    label: code,
    description: "",
    indexRequirements,
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: false,
    labSelectable: true,
    labOnly: true,
    singleTurnBenchmarkSelectable: true,
    ...overrides,
  };
}

describe("LabRagIndexingMaterializationPlan", () => {
  it("shows preset-derived chunk and hybrid groups", () => {
    render(
      <IntlTestProvider locale="en">
        <LabRagIndexingMaterializationPlan
          selectedPresetCodes={["P3", "P8", "P10"]}
          experimentalPresets={[
            {
              productPresetId: "P3",
              code: "P3",
              family: "x",
              label: "P3",
              description: "",
              indexRequirements: { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: false },
              requiredCapabilities: [],
              supported: true,
              supportStatus: "EXECUTABLE",
              reasonIfUnsupported: null,
              requiresMultiTurn: false,
              mapsToRuntimeCapabilities: {},
              allowedOutcomes: ["EXECUTED"],
              chatSelectable: false,
              labSelectable: true,
              labOnly: true,
              singleTurnBenchmarkSelectable: true,
            },
            {
              productPresetId: "P8",
              code: "P8",
              family: "x",
              label: "P8",
              description: "",
              indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
              requiredCapabilities: [],
              supported: true,
              supportStatus: "EXECUTABLE",
              reasonIfUnsupported: null,
              requiresMultiTurn: false,
              mapsToRuntimeCapabilities: {},
              allowedOutcomes: ["EXECUTED"],
              chatSelectable: false,
              labSelectable: true,
              labOnly: true,
              singleTurnBenchmarkSelectable: true,
            },
            {
              productPresetId: "P10",
              code: "P10",
              family: "x",
              label: "P10",
              description: "",
              indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
              requiredCapabilities: [],
              supported: true,
              supportStatus: "EXECUTABLE",
              reasonIfUnsupported: null,
              requiresMultiTurn: false,
              mapsToRuntimeCapabilities: {},
              allowedOutcomes: ["EXECUTED"],
              chatSelectable: false,
              labSelectable: true,
              labOnly: true,
              singleTurnBenchmarkSelectable: true,
            },
          ]}
          autoReindex
          reuseCompatibleActiveSnapshot
          indexReadiness={null}
          onAutoReindexChange={vi.fn()}
          onReuseCompatibleChange={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByTestId("lab-rag-group-CHUNK_LEVEL")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-group-HYBRID_METADATA")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-auto-reindex")).toBeChecked();
  });

  it("shows empty guidance when no presets are selected", () => {
    render(
      <IntlTestProvider locale="en">
        <LabRagIndexingMaterializationPlan
          selectedPresetCodes={[]}
          experimentalPresets={[]}
          autoReindex={false}
          reuseCompatibleActiveSnapshot={false}
          indexReadiness={null}
          onAutoReindexChange={vi.fn()}
          onReuseCompatibleChange={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByTestId("lab-rag-materialization-empty")).toBeInTheDocument();
  });

  it("labels direct, no-index, document, and multi-turn groups", () => {
    render(
      <IntlTestProvider locale="en">
        <LabRagIndexingMaterializationPlan
          selectedPresetCodes={["P0", "P1", "P2", "P9"]}
          experimentalPresets={[
            preset("P0", null),
            preset("P1", null),
            preset("P2", { requiredMaterializationStrategy: "DOCUMENT_LEVEL", requiresMetadataSupport: false }),
            preset("P9", { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: false }, {
              requiresMultiTurn: true,
              singleTurnBenchmarkSelectable: false,
            }),
          ]}
          autoReindex={false}
          reuseCompatibleActiveSnapshot={false}
          indexReadiness={null}
          onAutoReindexChange={vi.fn()}
          onReuseCompatibleChange={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByTestId("lab-rag-group-DIRECT_LLM")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-group-NO_INDEX")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-group-DOCUMENT_LEVEL")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-group-MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN")).toBeInTheDocument();
    expect(screen.getAllByText(/No vector index required/i).length).toBeGreaterThan(0);
  });

  it("forwards checkbox changes and disables reuse when auto reindex is off", async () => {
    const onAutoReindexChange = vi.fn();
    const onReuseCompatibleChange = vi.fn();
    const user = userEvent.setup();

    render(
      <IntlTestProvider locale="en">
        <LabRagIndexingMaterializationPlan
          selectedPresetCodes={["P3"]}
          experimentalPresets={[
            preset("P3", { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: false }),
          ]}
          autoReindex={false}
          reuseCompatibleActiveSnapshot={false}
          indexReadiness={null}
          onAutoReindexChange={onAutoReindexChange}
          onReuseCompatibleChange={onReuseCompatibleChange}
        />
      </IntlTestProvider>,
    );

    const reuse = screen.getByTestId("lab-rag-reuse-compatible-snapshot");
    expect(reuse).toBeDisabled();

    await user.click(screen.getByTestId("lab-rag-auto-reindex"));
    expect(onAutoReindexChange).toHaveBeenCalledWith(true);
  });
});
