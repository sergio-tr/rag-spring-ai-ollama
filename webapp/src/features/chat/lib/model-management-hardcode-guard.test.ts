import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const LEGACY_MODEL_IDS = ["gemma3:4b", "mistral:7b", "llama3.1:8b", "mxbai-embed-large:latest"] as const;

const SCAN_ROOTS = ["src/features", "src/app", "src/components"];

const KNOWN_DOCUMENTED_EXCEPTIONS = ["lab-evaluation-draft.ts"] as const;

const ALLOWLIST_PATTERNS = [
  /\.test\.(ts|tsx)$/,
  /\.spec\.(ts|tsx)$/,
  /llm-campaign-preferred-models/,
  /embedding-campaign-preferred-models/,
  /admin-model-errors/,
  /model-management-hardcode-guard/,
];

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectSourceFiles(full, out);
    } else if (/\.(tsx?)$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

function isAllowlisted(path: string): boolean {
  return ALLOWLIST_PATTERNS.some((p) => p.test(path));
}

describe("model-management hardcode guard", () => {
  const webappRoot = process.cwd();

  it("legacy model ids appear only in allowlisted test/fixture files", () => {
    const violations: string[] = [];
    for (const root of SCAN_ROOTS) {
      const abs = join(webappRoot, root);
      try {
        for (const file of collectSourceFiles(abs)) {
          if (isAllowlisted(file)) continue;
          const text = readFileSync(file, "utf8");
          for (const modelId of LEGACY_MODEL_IDS) {
            if (text.includes(modelId)) {
              violations.push(`${file}: ${modelId}`);
            }
          }
        }
      } catch {
        // root may be absent in partial workspaces
      }
    }

    const undocumented = violations.filter(
      (v) => !KNOWN_DOCUMENTED_EXCEPTIONS.some((exception) => v.includes(exception)),
    );
    expect(undocumented).toEqual([]);
  });

  it("preferred-models module is not imported by chat configuration UI", () => {
    const chatPage = readFileSync(join(webappRoot, "src/app/[locale]/(app)/chat/page.tsx"), "utf8");
    expect(chatPage).not.toMatch(/llm-campaign-preferred-models/);
    expect(chatPage).not.toMatch(/LLM_CAMPAIGN_PREFERRED_MODEL_IDS/);
  });
  
  it("admin panel uses catalog API instead of legacy allowlist listing", () => {
    const adminPage = readFileSync(join(webappRoot, "src/app/[locale]/(app)/admin/page.tsx"), "utf8");
    const catalogHook = readFileSync(
      join(webappRoot, "src/features/admin/hooks/use-llm-catalog.ts"),
      "utf8",
    );
    expect(adminPage).toMatch(/useLlmCatalog/);
    expect(catalogHook).toMatch(/\/llm\/catalog/);
    expect(adminPage).not.toMatch(/AdminModelEntryDto/);
    expect(adminPage).not.toMatch(/\/admin\/models[^/]/);
    expect(adminPage).not.toMatch(/llm-campaign-preferred-models/);
  });

  it("lab evaluation uses catalog API instead of preferred model lists", () => {
    const labCard = readFileSync(
      join(webappRoot, "src/features/lab/components/lab-evaluation-run-card.tsx"),
      "utf8",
    );
    const labHook = readFileSync(
      join(webappRoot, "src/features/lab/hooks/use-lab-evaluation-models.ts"),
      "utf8",
    );
    expect(labCard).toMatch(/useLabEvaluationModels/);
    expect(labHook).toMatch(/\/lab\/evaluation-models/);
    expect(labCard).not.toMatch(/useModelsByType/);
    expect(labCard).not.toMatch(/embedding-campaign-preferred-models/);
    expect(labCard).not.toMatch(/llm-campaign-preferred-models/);
  });
});
