import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");

function readMessages(locale: "en" | "es"): Record<string, unknown> {
  const raw = readFileSync(join(root, "messages", `${locale}.json`), "utf8");
  return JSON.parse(raw) as Record<string, unknown>;
}

function flattenStrings(node: unknown, out: string[] = []): string[] {
  if (typeof node === "string") {
    out.push(node);
    return out;
  }
  if (node && typeof node === "object") {
    for (const v of Object.values(node as Record<string, unknown>)) {
      flattenStrings(v, out);
    }
  }
  return out;
}

const FORBIDDEN_IN_LAB_MESSAGES = [
  /POST JSON/i,
  /canonical benchmark API/i,
  /Stopped watching here/i,
  /Stopped waiting — the server job/i,
  /corpus and snapshot preparation are project-scoped/i,
  /Select an active project before running a RAG preset benchmark/i,
  /Status poll:/i,
  /Live stream:/i,
  /Lab API —/i,
  /POST \/api/i,
  /nomic-embed-text/i,
];

describe("LAB closure i18n guards", () => {
  for (const locale of ["en", "es"] as const) {
    it(`Lab namespace in ${locale}.json has no forbidden user-facing copy`, () => {
      const all = readMessages(locale);
      const lab = all.Lab as Record<string, unknown> | undefined;
      expect(lab).toBeDefined();
      const strings = flattenStrings(lab);
      for (const s of strings) {
        for (const re of FORBIDDEN_IN_LAB_MESSAGES) {
          expect(s, `Forbidden pattern ${re} in: ${s.slice(0, 80)}`).not.toMatch(re);
        }
      }
    });
  }
});
