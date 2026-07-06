import { describe, expect, it } from "vitest";
import en from "../../../../messages/en.json";
import es from "../../../../messages/es.json";
import {
  isPresetBaseFeature,
  isPresetControlledOffFeature,
  isPresetControlledBooleanKey,
} from "./preset-base-feature-locking";

/** Mirrors {@code ChatProductPresetAlignment.demoBestProductValues()} boolean flags. */
const demoBestBase = {
  useRetrieval: true,
  useAdvisor: true,
  naiveFullCorpusInPromptEnabled: false,
  expansionEnabled: true,
  nerEnabled: true,
  toolsEnabled: true,
  functionCallingEnabled: true,
  postRetrievalEnabled: true,
  clarificationEnabled: true,
  reasoningEnabled: false,
  rankerEnabled: false,
  judgeEnabled: false,
  memoryEnabled: false,
  adaptiveRoutingEnabled: false,
};

const FORBIDDEN_ARCHITECTURAL_KEYS = [
  "useRetrieval",
  "metadataEnabled",
  "materializationStrategy",
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "deterministicToolRoutingEnabled",
  "functionCallingEnabled",
  "useAdvisor",
  "reasoningEnabled",
  "rankerEnabled",
  "postRetrievalEnabled",
  "adaptiveRoutingEnabled",
  "judgeEnabled",
  "clarificationEnabled",
  "memoryEnabled",
] as const;

describe("chat-safe-customization-validation closeout", () => {
  it("covers all forbidden architectural boolean keys in preset locking", () => {
    for (const key of FORBIDDEN_ARCHITECTURAL_KEYS) {
      if (key === "metadataEnabled" || key === "materializationStrategy" || key === "deterministicToolRoutingEnabled") {
        expect(isPresetControlledBooleanKey(key)).toBe(false);
      } else {
        expect(isPresetControlledBooleanKey(key)).toBe(true);
      }
    }
  });

  it("Demo_Best locks enabled base features and defers disabled add-ons", () => {
    expect(isPresetBaseFeature("useRetrieval", demoBestBase)).toBe(true);
    expect(isPresetBaseFeature("toolsEnabled", demoBestBase)).toBe(true);
    expect(isPresetBaseFeature("clarificationEnabled", demoBestBase)).toBe(true);
    expect(isPresetControlledOffFeature("rankerEnabled", demoBestBase)).toBe(true);
    expect(isPresetControlledOffFeature("memoryEnabled", demoBestBase)).toBe(true);
    expect(isPresetControlledOffFeature("judgeEnabled", demoBestBase)).toBe(true);
  });

  it("retrieval override i18n keys exist in EN and ES", () => {
    const keys = [
      "retrievalOverrideModeLegend",
      "retrievalOverrideModePreset",
      "retrievalOverrideModeProjectSettings",
      "retrievalOverrideModeAssistantDefaults",
      "retrievalOverrideModeCustom",
      "chatFeatureTipEnabledByPreset",
      "chatFeatureTipPresetControlled",
    ] as const;
    for (const key of keys) {
      expect(en.Chat[key]).toBeTruthy();
      expect(es.Chat[key]).toBeTruthy();
    }
  });
});
