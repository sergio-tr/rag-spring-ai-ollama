import { describe, expect, it } from "vitest";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import {
  filterLabBenchmarkSelectablePresets,
  findInvalidLabPresetSelections,
  isCoreExperimentalPresetCode,
  isLabBenchmarkPresetSelectable,
  listCoreExperimentalPresetCodes,
  sanitizeLabBenchmarkDraftPresetCodes,
} from "./experimental-preset-selection";

function preset(code: string, overrides: Partial<ExperimentalPresetCatalogItemDto> = {}): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: `id-${code}`,
    code,
    family: "S2",
    label: code,
    description: "",
    indexRequirements: null,
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: true,
    labSelectable: true,
    labOnly: false,
    corpusRequired: true,
    requiresSnapshot: true,
    requiresProjectDocuments: true,
    singleTurnBenchmarkSelectable: true,
    protocolStageIndex: 0,
    parentPresetCode: null,
    effectiveTerminalRuntimeJson: "{}",
    ...overrides,
  };
}

describe("experimental-preset-selection", () => {
  it("isLabBenchmarkPresetSelectable requires supported and single-turn", () => {
    expect(isLabBenchmarkPresetSelectable(preset("P2"))).toBe(true);
    expect(
      isLabBenchmarkPresetSelectable(
        preset("P13", { singleTurnBenchmarkSelectable: false, supported: true }),
      ),
    ).toBe(false);
    expect(isLabBenchmarkPresetSelectable(preset("P8", { supported: false }))).toBe(false);
  });

  it("isCoreExperimentalPresetCode matches P0-P10 and P15", () => {
    expect(isCoreExperimentalPresetCode("P0")).toBe(true);
    expect(isCoreExperimentalPresetCode("P10")).toBe(true);
    expect(isCoreExperimentalPresetCode("P15")).toBe(true);
    expect(isCoreExperimentalPresetCode("P11")).toBe(false);
    expect(isCoreExperimentalPresetCode("P12")).toBe(false);
    expect(isCoreExperimentalPresetCode("P13")).toBe(false);
    expect(isCoreExperimentalPresetCode("P8")).toBe(true);
  });

  it("listCoreExperimentalPresetCodes filters catalog", () => {
    const codes = listCoreExperimentalPresetCodes([
      preset("P0"),
      preset("P13", { singleTurnBenchmarkSelectable: false, labSelectable: false }),
      preset("P10"),
      preset("P11", { singleTurnBenchmarkSelectable: false, labSelectable: false }),
    ]);
    expect(codes).toEqual(["P0", "P10"]);
  });

  it("findInvalidLabPresetSelections flags unknown and non-lab presets", () => {
    const catalog = [preset("P0"), preset("P14", { singleTurnBenchmarkSelectable: false })];
    expect(findInvalidLabPresetSelections(["P0", "P14", "P99"], catalog)).toEqual(["P14", "P99"]);
    expect(filterLabBenchmarkSelectablePresets(catalog).map((p) => p.code)).toEqual(["P0"]);
  });

  it("sanitizeLabBenchmarkDraftPresetCodes removes P11-P14 before catalog loads", () => {
    const { selected, removed } = sanitizeLabBenchmarkDraftPresetCodes(
      ["P0", "P11", "P12", "P13", "P14"],
      undefined,
      false,
    );
    expect(selected).toEqual(["P0"]);
    expect(removed).toEqual(["P11", "P12", "P13", "P14"]);
  });

  it("sanitizeLabBenchmarkDraftPresetCodes removes non-lab-selectable codes when catalog is ready", () => {
    const catalog = [preset("P0"), preset("P14", { singleTurnBenchmarkSelectable: false, labSelectable: false })];
    const { selected, removed } = sanitizeLabBenchmarkDraftPresetCodes(["P0", "P14", "P99"], catalog, true);
    expect(selected).toEqual(["P0"]);
    expect(removed).toEqual(["P14", "P99"]);
  });
});
