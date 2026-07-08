import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const MESSAGES_DIR = join(__dirname, "../../messages");

function loadLocaleMessages(locale: "en" | "es"): Record<string, unknown> {
  return JSON.parse(readFileSync(join(MESSAGES_DIR, `${locale}.json`), "utf8")) as Record<string, unknown>;
}

function flattenMessages(obj: Record<string, unknown>, prefix = ""): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === "string") {
      out[path] = value;
    } else if (value && typeof value === "object" && !Array.isArray(value)) {
      Object.assign(out, flattenMessages(value as Record<string, unknown>, path));
    }
  }
  return out;
}

const FORBIDDEN_USER_FACING_PATTERNS: Array<{ pattern: RegExp; label: string }> = [
  { pattern: /\bServer default\b/i, label: "Server default" },
  { pattern: /\bAssistant defaults\b/i, label: "Assistant defaults" },
  { pattern: /\bUser RAG config\b/i, label: "User RAG config" },
  { pattern: /\bContains expected answer\b/i, label: "Contains expected answer" },
  { pattern: /\bHallucination\b/i, label: "Hallucination" },
  { pattern: /\bGovernance allowed\b/i, label: "Governance allowed" },
  { pattern: /\bDescription \(optional\)\b/i, label: "Description (optional)" },
];

const CANONICAL_REPLACEMENTS: Array<{ key: string; expected: RegExp }> = [
  { key: "Lab.benchmarkColContainsExpected", expected: /Expected phrase found \(literal\)/i },
  { key: "Lab.benchmarkColHallucination", expected: /Ungrounded rate/i },
  { key: "Chat.presetServerDefault", expected: /Production assistant configuration/i },
  { key: "Projects.metadataIndexLabel", expected: /Metadata-aware index capability/i },
  { key: "Projects.description", expected: /Short description/i },
];

describe("A9 residual copy - user-facing i18n", () => {
  const en = flattenMessages(loadLocaleMessages("en"));

  it("does not expose forbidden legacy labels in English messages", () => {
    const offenders: string[] = [];
    for (const [key, value] of Object.entries(en)) {
      for (const { pattern, label } of FORBIDDEN_USER_FACING_PATTERNS) {
        if (pattern.test(value)) {
          offenders.push(`${key}: ${label}`);
        }
      }
    }
    expect(offenders, offenders.join("\n")).toEqual([]);
  });

  it("uses canonical replacement labels where expected", () => {
    for (const { key, expected } of CANONICAL_REPLACEMENTS) {
      const value = en[key];
      expect(value, `missing key ${key}`).toBeDefined();
      expect(value!, key).toMatch(expected);
    }
  });

  it("keeps runtimeBaseExpand and runtimeEffectiveKeyCount only as advanced-section copy", () => {
    const expand = en["Chat.runtimeBaseExpand"];
    const keyCount = en["Chat.runtimeEffectiveKeyCount"];
    expect(expand).toBe("Show full base effective config");
    expect(keyCount).toMatch(/^Effective keys:/);
  });
});
