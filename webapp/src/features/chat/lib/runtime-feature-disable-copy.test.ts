import { describe, it, expect } from "vitest";
import {
  clientSideDisableTip,
  formatDisabledRuntimeFeatureTip,
} from "./runtime-feature-disable-copy";
import type { DisabledRuntimeFeatureDto, RuntimeConfigCapabilityDto } from "@/types/api";

const t = (key: string) =>
  ({
    chatFeatureTipRequiresRetrieval: "Requires retrieval",
    chatFeatureTipRequiresTools: "Requires tools",
    chatFeatureTipIncompatibleFullContext: "Incompatible with full-context",
    chatFeatureTipIndexNotSupported: "Not supported by this index",
    chatFeatureTipRequiresVectorChunks: "Requires vector chunks",
    chatFeatureTipNotAvailableInChat: "Not available in chat runtime",
    chatFeatureTipEnabledByPreset: "Enabled by preset",
    chatFeatureTipPresetControlled: "Controlled by preset",
  })[key] ?? key;

describe("formatDisabledRuntimeFeatureTip", () => {
  it.each([
    ["REQUIRES_useRetrieval", "Requires retrieval"],
    ["REQUIRES_toolsEnabled", "Requires tools"],
    ["REQUIRES_tools", "Requires tools"],
    ["EXCLUDES_naiveFullCorpusInPromptEnabled", "Incompatible with full-context"],
    ["NOT_CONFIGURABLE_IN_CHAT", "Not available in chat runtime"],
    ["STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED", "Not supported by this index"],
    ["STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED", "Requires vector chunks"],
    ["PRESET_BASE_FEATURE_LOCKED", "Enabled by preset"],
    ["PRESET_FEATURE_TOGGLE_DEFERRED", "Controlled by preset"],
  ] as const)("maps %s to short tip", (reasonCode, expected) => {
    const item: DisabledRuntimeFeatureDto = { key: "x", reasonCode, reason: "long backend message" };
    expect(formatDisabledRuntimeFeatureTip(item, t)).toBe(expected);
  });
});

describe("clientSideDisableTip", () => {
  const cap = (requires: string[] = [], excludes: string[] = []): RuntimeConfigCapabilityDto => ({
    key: "test",
    label: "test",
    description: "",
    category: "RUNTIME_HOT_SWAPPABLE",
    visibleInChat: true,
    configurableInChat: true,
    implemented: true,
    engineWired: true,
    supportMode: null,
    displayOrder: 1,
    requires,
    excludes,
    requiresIndexSnapshot: false,
    requiresReindexWhenChanged: false,
    reasonIfDisabled: null,
    reasonIfNotImplemented: null,
  });

  it("returns Requires retrieval when useRetrieval is required but off", () => {
    expect(
      clientSideDisableTip("useAdvisor", cap(["useRetrieval"]), { useRetrieval: false }, t),
    ).toBe("Requires retrieval");
  });

  it("returns Requires tools for metadata without tools", () => {
    expect(
      clientSideDisableTip(
        "metadataEnabled",
        undefined,
        { metadataEnabled: true, toolsEnabled: false },
        t,
      ),
    ).toBe("Requires tools");
  });
});
