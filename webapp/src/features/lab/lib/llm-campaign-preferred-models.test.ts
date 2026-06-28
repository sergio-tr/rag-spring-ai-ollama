import { describe, expect, it } from "vitest";
import {
  LLM_CAMPAIGN_PREFERRED_MODEL_IDS,
  llmComparisonAvailabilityStatus,
  missingPreferredLlmModels,
} from "@/features/lab/lib/llm-campaign-preferred-models";

describe("llm-campaign-preferred-models", () => {
  it("flags missing preferred tags", () => {
    expect(missingPreferredLlmModels(["llama3.1:8b"], LLM_CAMPAIGN_PREFERRED_MODEL_IDS)).toEqual([
      "gemma3:4b",
      "mistral:7b",
    ]);
  });

  it("reports READY when two or more models are selectable", () => {
    expect(llmComparisonAvailabilityStatus(2)).toBe("READY");
    expect(llmComparisonAvailabilityStatus(1)).toBe("BLOCKED_BY_MODEL_AVAILABILITY");
  });
});
