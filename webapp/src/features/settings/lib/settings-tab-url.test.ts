import { describe, expect, it } from "vitest";
import { resolveSettingsPathFromTabQuery, SETTINGS_TAB_QUERY_IDS } from "./settings-tab-url";

describe("resolveSettingsPathFromTabQuery", () => {
  it("maps known tab ids to segmented routes (case-insensitive)", () => {
    expect(resolveSettingsPathFromTabQuery("account")).toBe("/settings/account");
    expect(resolveSettingsPathFromTabQuery("ACCOUNT")).toBe("/settings/account");
    expect(resolveSettingsPathFromTabQuery(" user ")).toBe("/settings/user");
  });

  it("maps general to /settings", () => {
    expect(resolveSettingsPathFromTabQuery("general")).toBe("/settings");
  });

  it("falls back to /settings for unknown or empty tab values", () => {
    expect(resolveSettingsPathFromTabQuery("nope")).toBe("/settings");
    expect(resolveSettingsPathFromTabQuery("")).toBe("/settings");
    expect(resolveSettingsPathFromTabQuery("   ")).toBe("/settings");
  });

  it("treats null as /settings", () => {
    expect(resolveSettingsPathFromTabQuery(null)).toBe("/settings");
  });

  it("exposes stable lowercase tab ids list", () => {
    expect(SETTINGS_TAB_QUERY_IDS).toContain("account");
    expect(SETTINGS_TAB_QUERY_IDS).toContain("general");
  });
});
