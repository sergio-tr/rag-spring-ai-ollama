import { describe, it, expect } from "vitest";
import { localizedPath } from "./localized-path";

describe("localizedPath", () => {
  it("prefixes internal paths with the locale", () => {
    expect(localizedPath("/settings/user", "en")).toBe("/en/settings/user");
    expect(localizedPath("/settings/user", "es")).toBe("/es/settings/user");
    expect(localizedPath("/settings/project", "en")).toBe("/en/settings/project");
    expect(localizedPath("/lab/evaluation/rag", "es")).toBe("/es/lab/evaluation/rag");
  });

  it("replaces an existing locale segment instead of duplicating it", () => {
    expect(localizedPath("/en/settings/user", "es")).toBe("/es/settings/user");
    expect(localizedPath("/es/settings/user", "en")).toBe("/en/settings/user");
  });

  it("preserves query strings and hash fragments", () => {
    expect(localizedPath("/settings/user?tab=defaults", "en")).toBe(
      "/en/settings/user?tab=defaults",
    );
    expect(localizedPath("/settings/user#models", "es")).toBe("/es/settings/user#models");
    expect(localizedPath("/settings/user?tab=defaults#models", "en")).toBe(
      "/en/settings/user?tab=defaults#models",
    );
  });

  it("passes through external and special links unchanged", () => {
    expect(localizedPath("https://example.com/settings/user", "en")).toBe(
      "https://example.com/settings/user",
    );
    expect(localizedPath("mailto:test@example.com", "en")).toBe("mailto:test@example.com");
    expect(localizedPath("tel:+123", "en")).toBe("tel:+123");
    expect(localizedPath("#defaults", "en")).toBe("#defaults");
  });

  it("does not prefix API routes", () => {
    expect(localizedPath("/api/v5/auth/session", "en")).toBe("/api/v5/auth/session");
  });

  it("localizes the root path", () => {
    expect(localizedPath("/", "en")).toBe("/en");
    expect(localizedPath("/", "es")).toBe("/es");
  });

  it("returns empty href unchanged", () => {
    expect(localizedPath("", "en")).toBe("");
  });

  it("localizes relative paths without a leading slash", () => {
    expect(localizedPath("settings/user", "en")).toBe("/en/settings/user");
  });

  it("does not prefix Next.js asset routes", () => {
    expect(localizedPath("/_next/static/chunk.js", "en")).toBe("/_next/static/chunk.js");
  });

  it("passes through blob and data URLs unchanged", () => {
    expect(localizedPath("blob:https://localhost/abc", "en")).toBe("blob:https://localhost/abc");
    expect(localizedPath("data:text/plain,hello", "en")).toBe("data:text/plain,hello");
  });
});
