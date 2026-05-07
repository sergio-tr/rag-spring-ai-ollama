import { describe, expect, it } from "vitest";
import {
  CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
  resolveChatPresetLabel,
  resolveChatPresetSelectValue,
  resolveConversationPresetSelectValue,
  resolvePresetSelectLabel,
} from "./conversation-preset-ui";

const labels = {
  systemSuffix: "system",
  recommendedDefault: "Recommended default",
  defaultConfiguration: "Default configuration",
};

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
      labels,
    );
    expect(label).toBe("MyPreset");
  });

  it("resolvePresetSelectLabel uses recommended default when catalog missing row", () => {
    expect(resolvePresetSelectLabel(undefined, CHAT_DETERMINISTIC_DEFAULT_PRESET_ID, labels)).toBe(
      "Recommended default",
    );
  });

  it("resolvePresetSelectLabel uses default configuration when catalog loaded empty", () => {
    expect(resolvePresetSelectLabel([], CHAT_DETERMINISTIC_DEFAULT_PRESET_ID, labels)).toBe(
      "Default configuration",
    );
  });

  it("resolveChatPresetSelectValue prefers persisted presetId", () => {
    expect(
      resolveChatPresetSelectValue(
        {
          id: "c",
          title: "t",
          updatedAt: "",
          presetId: "picked",
          effectivePresetId: CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
        },
        [{ id: "picked", name: "X", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }],
      ),
    ).toBe("picked");
  });

  it("resolveChatPresetSelectValue uses deterministic catalog row when conversation ids absent", () => {
    expect(
      resolveChatPresetSelectValue(
        { id: "c", title: "t", updatedAt: "", presetId: null, effectivePresetId: null },
        [
          { id: "other", name: "O", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
          {
            id: CHAT_DETERMINISTIC_DEFAULT_PRESET_ID,
            name: "Demo",
            description: null,
            tags: [],
            values: {},
            system: true,
            createdAt: "",
            updatedAt: "",
          },
        ],
      ),
    ).toBe(CHAT_DETERMINISTIC_DEFAULT_PRESET_ID);
  });

  it("resolveChatPresetSelectValue uses first system preset when deterministic absent", () => {
    expect(
      resolveChatPresetSelectValue(
        { id: "c", title: "t", updatedAt: "", presetId: null, effectivePresetId: null },
        [
          { id: "a", name: "A", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
          { id: "s", name: "S", description: null, tags: [], values: {}, system: true, createdAt: "", updatedAt: "" },
        ],
      ),
    ).toBe("s");
  });

  it("resolveChatPresetSelectValue uses first preset when no system preset", () => {
    expect(
      resolveChatPresetSelectValue(
        { id: "c", title: "t", updatedAt: "", presetId: null, effectivePresetId: null },
        [{ id: "first", name: "F", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }],
      ),
    ).toBe("first");
  });

  it("resolveChatPresetSelectValue falls back to deterministic id without catalog", () => {
    expect(resolveChatPresetSelectValue(undefined, undefined)).toBe(CHAT_DETERMINISTIC_DEFAULT_PRESET_ID);
  });

  it("resolveChatPresetLabel shows experimental code+label when selectedPresetId is experimental productPresetId", () => {
    const label = resolveChatPresetLabel(
      [{ id: "pr1", name: "Prod", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }],
      [
        {
          productPresetId: "exp4",
          code: "P4",
          family: "TFG",
          label: "Chunk + metadata retrieval",
          description: "",
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
        },
      ],
      "exp4",
      labels,
    );
    expect(label).toBe("P4 — Chunk + metadata retrieval");
  });

  it("resolveChatPresetLabel shows Recommended Default when selectedPresetId is null", () => {
    expect(resolveChatPresetLabel([], [], null, labels)).toBe("Recommended default");
  });
});
