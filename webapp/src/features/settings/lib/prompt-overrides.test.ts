import { describe, expect, it } from "vitest";
import type { PromptCatalogGroup } from "@/features/settings/hooks/use-prompt-catalog";
import {
  PROMPT_OVERRIDES_KEY,
  effectivePromptContent,
  mergePromptOverrides,
  readPromptOverrides,
} from "@/features/settings/lib/prompt-overrides";

describe("prompt-overrides", () => {
  const group: PromptCatalogGroup = {
    id: "system",
    componentLabel: "System",
    defaultContent: "Default system prompt",
    defaultSystemContent: "Default system prompt",
    requiredVariables: [],
    optionalVariables: [],
    runtimeEditable: false,
    description: "Default system prompt",
  };

  it("readPromptOverrides returns empty object for missing values", () => {
    expect(readPromptOverrides(undefined)).toEqual({});
  });

  it("readPromptOverrides ignores non-object promptOverrides entries", () => {
    expect(readPromptOverrides({ [PROMPT_OVERRIDES_KEY]: null })).toEqual({});
    expect(readPromptOverrides({ [PROMPT_OVERRIDES_KEY]: ["x"] })).toEqual({});
    expect(readPromptOverrides({ [PROMPT_OVERRIDES_KEY]: "text" })).toEqual({});
  });

  it("readPromptOverrides keeps only string values", () => {
    expect(
      readPromptOverrides({
        [PROMPT_OVERRIDES_KEY]: {
          system: "Custom",
          ignored: 42,
          alsoIgnored: null,
        },
      }),
    ).toEqual({ system: "Custom" });
  });

  it("effectivePromptContent uses trimmed custom override when present", () => {
    expect(effectivePromptContent(group, { system: "  Override  " })).toBe("  Override  ");
  });

  it("effectivePromptContent falls back when override is blank", () => {
    expect(effectivePromptContent(group, { system: "   " })).toBe("Default system prompt");
    expect(effectivePromptContent(group, {})).toBe("Default system prompt");
  });

  it("mergePromptOverrides removes key when all overrides are blank", () => {
    expect(
      mergePromptOverrides(
        { [PROMPT_OVERRIDES_KEY]: { system: "old" }, other: true },
        { system: "   ", planner: "" },
      ),
    ).toEqual({ other: true });
  });

  it("mergePromptOverrides stores trimmed non-empty overrides", () => {
    expect(mergePromptOverrides({ other: true }, { system: "  New prompt  ", planner: " " })).toEqual({
      other: true,
      [PROMPT_OVERRIDES_KEY]: { system: "  New prompt  " },
    });
  });
});
