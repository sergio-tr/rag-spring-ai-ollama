import { readdirSync, readFileSync, statSync } from "node:fs";
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

const FORBIDDEN_IN_PRODUCT_MESSAGES = [
  /POST JSON/i,
  /canonical benchmark API/i,
  /canonical benchmarks/i,
  /\bM9 experimental evidence\b/i,
  /\bM10\b|\bM11\b|\bM12\b|\bM9\b/i,
  /Do not claim/i,
  /claim map/i,
  /handoff/i,
  /\bTFG\b/i,
  /partial evidence/i,
  /Jaeger verified/i,
  /RAG ladder complete/i,
  /not comparable to P0 bench/i,
  /Historical audit only/i,
  /Canonical paths refer to/i,
  /unsupported by design/i,
  /Stopped watching here/i,
  /Stopped waiting — the server job/i,
  /corpus and snapshot preparation are project-scoped/i,
  /Select an active project before running a RAG preset benchmark/i,
  /\bcorpus\b/i,
  /\bcorpus de evaluación\b/i,
  /Status poll:/i,
  /Live stream:/i,
  /Lab API —/i,
  /POST \/api/i,
  /typed evaluation_dataset/i,
  /Async jobs:/i,
  /SSE endpoint:/i,
  /Poll endpoint:/i,
  /nomic-embed-text/i,
  /honest outcomes/i,
  /external appendix/i,
  /single-turn harness/i,
  /m9-lab-experimental-evidence/i,
  /\bRAG_PRESET_END_TO_END\b/,
  /\bLLM_JUDGE_QA\b/,
  /\bEMBEDDING_RETRIEVAL\b/,
  /\bFUTURE_MULTI_TURN_NOT_SELECTABLE\b/,
  /\bLLM_MODEL_BASELINE\b/,
  /\bRAG_PRESET_BENCHMARK\b/,
  /\bEXECUTED\b.*\bSKIPPED\b.*\bFAILED\b/,
];

const FORBIDDEN_IN_LAB_MESSAGES = FORBIDDEN_IN_PRODUCT_MESSAGES;

const FORBIDDEN_IN_LAB_SRC = [
  /Lab API —/i,
  /canonical benchmarks/i,
  /POST \/api\/v\d/i,
  /GET \/api\/v\d/i,
  /typed evaluation_dataset/i,
  /Async jobs:/i,
  /Status poll:/i,
  /Live stream:/i,
];

function walkTsFiles(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) {
      walkTsFiles(full, out);
    } else if (/\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name)) {
      out.push(full);
    }
  }
  return out;
}

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

    it(`Chat namespace in ${locale}.json has no forbidden user-facing copy`, () => {
      const all = readMessages(locale);
      const chat = all.Chat as Record<string, unknown> | undefined;
      expect(chat).toBeDefined();
      const strings = flattenStrings(chat);
      for (const s of strings) {
        for (const re of FORBIDDEN_IN_PRODUCT_MESSAGES) {
          expect(s, `Forbidden pattern ${re} in: ${s.slice(0, 80)}`).not.toMatch(re);
        }
      }
    });
  }
});

describe("LAB UX-001 source guards", () => {
  it("lab feature sources avoid forbidden technical copy in user paths", () => {
    const labRoot = dirname(fileURLToPath(import.meta.url));
    const appLab = join(root, "src/app/[locale]/(app)/lab");
    const dirs = [labRoot, appLab].filter((d) => {
      try {
        return statSync(d).isDirectory();
      } catch {
        return false;
      }
    });
    const files = dirs.flatMap((d) => walkTsFiles(d));
    expect(files.length).toBeGreaterThan(0);
    for (const file of files) {
      const src = readFileSync(file, "utf8");
      for (const re of FORBIDDEN_IN_LAB_SRC) {
        expect(src, `Forbidden pattern ${re} in ${file}`).not.toMatch(re);
      }
    }
  });
});
