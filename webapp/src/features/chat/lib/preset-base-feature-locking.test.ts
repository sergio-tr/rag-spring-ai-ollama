import { describe, it, expect } from "vitest";
import {
  isPresetBaseFeature,
  isPresetControlledOffFeature,
  presetBaseFeatures,
} from "./preset-base-feature-locking";

describe("preset-base-feature-locking", () => {
  const p3Base = {
    useRetrieval: true,
    expansionEnabled: false,
    toolsEnabled: false,
  };

  it("detects base features from preset effective config", () => {
    expect(isPresetBaseFeature("useRetrieval", p3Base)).toBe(true);
    expect(isPresetBaseFeature("expansionEnabled", p3Base)).toBe(false);
    expect(presetBaseFeatures(p3Base)).toEqual(["useRetrieval"]);
  });

  it("marks non-base features as preset-controlled off", () => {
    expect(isPresetControlledOffFeature("expansionEnabled", p3Base)).toBe(true);
    expect(isPresetControlledOffFeature("useRetrieval", p3Base)).toBe(false);
  });
});
