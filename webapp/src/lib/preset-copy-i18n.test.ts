import { describe, expect, it, vi } from "vitest";
import { createPresetCopyFn } from "./preset-copy-i18n";

describe("createPresetCopyFn", () => {
  it("routes preset display keys to Chat namespace", () => {
    const labT = vi.fn((key: string) => `lab:${key}`);
    const chatT = vi.fn((key: string) => `chat:${key}`);
    const copyT = createPresetCopyFn(labT, chatT);

    expect(copyT("presetDisplay.P0")).toBe("chat:presetDisplay.P0");
    expect(copyT("presetLatencyTier.fast")).toBe("chat:presetLatencyTier.fast");
    expect(copyT("chatPresetNotSelectable")).toBe("chat:chatPresetNotSelectable");
    expect(copyT("labConfigUnsupportedPreset")).toBe("lab:labConfigUnsupportedPreset");

    expect(chatT).toHaveBeenCalledWith("presetDisplay.P0");
    expect(labT).toHaveBeenCalledWith("labConfigUnsupportedPreset");
  });
});
