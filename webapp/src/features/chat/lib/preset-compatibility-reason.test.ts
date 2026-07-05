import { describe, expect, it } from "vitest";
import { formatPresetCompatibilityDisabledReason } from "./preset-compatibility-reason";

const t = (key: string) =>
  ({
    chatPresetCompatRequiresHybrid: "Requires HYBRID index",
    chatPresetCompatRequiresMetadata: "Requires metadata-aware index capability",
    chatPresetCompatStructuredSearchNoRetrieval:
      "Structured-search projects do not support retrieval-based RAG presets",
    chatPresetCompatNoActiveIndex: "No active index",
    chatPresetRequiresCompatibleIndex: "Requires a compatible index profile for this preset.",
  })[key] ?? key;

describe("formatPresetCompatibilityDisabledReason", () => {
  it("maps backend HYBRID materialization message", () => {
    const reason = formatPresetCompatibilityDisabledReason(
      {
        selectable: false,
        disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
        disabledReason: "Requires HYBRID index",
        indexRequirements: null,
        compatibleWithActiveIndex: false,
      },
      t,
    );
    expect(reason).toBe("Requires HYBRID index");
  });

  it("maps metadata support code", () => {
    const reason = formatPresetCompatibilityDisabledReason(
      {
        selectable: false,
        disabledReasonCode: "METADATA_SUPPORT_REQUIRED",
        disabledReason: "Requires metadata-aware index capability",
        indexRequirements: null,
        compatibleWithActiveIndex: false,
      },
      t,
    );
    expect(reason).toBe("Requires metadata-aware index capability");
  });

  it("returns null for selectable presets", () => {
    expect(
      formatPresetCompatibilityDisabledReason(
        {
          selectable: true,
          disabledReasonCode: null,
          disabledReason: null,
          indexRequirements: null,
          compatibleWithActiveIndex: true,
        },
        t,
      ),
    ).toBeNull();
  });
});
