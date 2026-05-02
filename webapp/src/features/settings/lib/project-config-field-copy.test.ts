import { describe, expect, it, vi } from "vitest";
import { labelProjectConfigField } from "./project-config-field-copy";

describe("labelProjectConfigField", () => {
  it("maps known keys via translator", () => {
    const t = vi.fn((k: string) => `translated:${k}`);
    expect(labelProjectConfigField("topK", t)).toBe("translated:projectConfigFieldTopK");
    expect(t).toHaveBeenCalledWith("projectConfigFieldTopK");
  });

  it("falls back to raw key when unknown", () => {
    const t = vi.fn(() => "unused");
    expect(labelProjectConfigField("futureKey", t)).toBe("futureKey");
    expect(t).not.toHaveBeenCalled();
  });
});
