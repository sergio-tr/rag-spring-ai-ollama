import { describe, expect, it } from "vitest";
import {
  CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
  resolveConversationPresetSelectValue,
  resolvePresetSelectLabel,
} from "./conversation-preset-ui";

describe("conversation-preset-ui", () => {
  it("resolveConversationPresetSelectValue uses effectivePresetId when presetId null", () => {
    expect(
      resolveConversationPresetSelectValue({
        id: "c",
        title: "t",
        updatedAt: "",
        presetId: null,
        effectivePresetId: CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
      }),
    ).toBe(CHAT_DETERMINISTIC_DEFAULT_PRESET_ID);
  });

  it("resolveConversationPresetSelectValue prefers persisted presetId over effectivePresetId", () => {
    expect(
      resolveConversationPresetSelectValue({
        id: "c",
        title: "t",
        updatedAt: "",
        presetId: "custom-id",
        effectivePresetId: CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
      }),
    ).toBe("custom-id");
  });

  it("resolveConversationPresetSelectValue falls back to deterministic id when missing", () => {
    expect(resolveConversationPresetSelectValue({ id: "c", title: "t", updatedAt: "" })).toBe(
      CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
    );
  });

  it("resolvePresetSelectLabel uses preset name when catalog contains id", () => {
    const label = resolvePresetSelectLabel(
      [{ id: "p1", name: "MyPreset", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }],
      "p1",
      { systemSuffix: "system", serverDefault: "Default" },
    );
    expect(label).toBe("MyPreset");
  });

  it("resolvePresetSelectLabel uses server default copy when id missing from catalog", () => {
    expect(
      resolvePresetSelectLabel(undefined, CHAT_DETERMINISTIC_DEFAULT_PRESET_ID, {
        systemSuffix: "system",
        serverDefault: "Recommended default",
      }),
    ).toBe("Recommended default");
  });
});
