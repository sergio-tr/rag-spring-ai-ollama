import { describe, expect, it } from "vitest";
import { chatRuntimeLabelKey } from "@/features/chat/lib/assistant-config-product-labels";

describe("assistant-config-product-labels", () => {
  it("maps known runtime capability keys to message keys", () => {
    expect(chatRuntimeLabelKey("useRetrieval")).toBe("runtimeFeatureUseRetrieval");
    expect(chatRuntimeLabelKey("judgeEnabled")).toBe("runtimeFeatureAnswerQualityChecks");
  });

  it("returns the capability key unchanged when no mapping exists", () => {
    expect(chatRuntimeLabelKey("customCapability")).toBe("customCapability");
  });
});
