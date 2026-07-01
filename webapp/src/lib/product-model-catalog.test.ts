import { describe, expect, it } from "vitest";
import { selectableCatalogModelIds, toConfigModelOptions } from "@/lib/product-model-catalog";
import type { MeSelectableLlmModelDto } from "@/types/api";

describe("product-model-catalog", () => {
  const rows: MeSelectableLlmModelDto[] = [
    {
      modelName: "gpt-test",
      displayName: "GPT Test",
      selectable: true,
      disabledReason: null,
      disabledReasonCode: null,
      usableAsDefault: true,
      runtimeStatus: "NOT_PROBED",
    },
    {
      modelName: "missing-local",
      displayName: "missing-local",
      selectable: false,
      disabledReason: "Model not installed",
      disabledReasonCode: "LLM_MODEL_UNAVAILABLE",
      usableAsDefault: false,
      runtimeStatus: "UNAVAILABLE",
    },
  ];

  it("maps selectable rows to config options", () => {
    expect(toConfigModelOptions(rows)).toEqual([
      { value: "gpt-test", label: "GPT Test", disabled: false },
      { value: "missing-local", label: "missing-local", disabled: true },
    ]);
  });

  it("falls back to modelName when displayName is blank", () => {
    const blankDisplay: MeSelectableLlmModelDto[] = [
      {
        ...rows[0]!,
        modelName: "raw-id",
        displayName: "   ",
      },
    ];
    expect(toConfigModelOptions(blankDisplay)).toEqual([
      { value: "raw-id", label: "raw-id", disabled: false },
    ]);
  });

  it("lists only selectable model ids", () => {
    expect(selectableCatalogModelIds(rows)).toEqual(["gpt-test"]);
  });
});
