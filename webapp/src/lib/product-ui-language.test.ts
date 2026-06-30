import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import en from "../../messages/en.json";
import es from "../../messages/es.json";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "./product-provider-labels";
import { FORBIDDEN_NORMAL_UI_STRING_PATTERNS } from "./forbidden-primary-ui-strings";
import { toProductPresetDisplayName } from "./product-preset-labels";
import { formatBenchmarkKindLabel } from "./product-copy";
import { formatPresetDisplay } from "@/features/lab/lib/lab-benchmark-labels";

const __dirname = dirname(fileURLToPath(import.meta.url));
const messagesDir = join(__dirname, "../../messages");

function collectStringValues(value: unknown, out: string[] = []): string[] {
  if (typeof value === "string") {
    out.push(value);
    return out;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectStringValues(item, out);
    return out;
  }
  if (value && typeof value === "object") {
    for (const child of Object.values(value as Record<string, unknown>)) {
      collectStringValues(child, out);
    }
  }
  return out;
}

const PRODUCT_NAMESPACES = ["Chat", "Settings", "Lab", "Admin", "Metadata"] as const;

const FORBIDDEN_PRIMARY_COPY: RegExp[] = [
  ...FORBIDDEN_NORMAL_UI_STRING_PATTERNS,
  /\bDemo_Best\b/,
  /\bDemo_Worst\b/,
  /\bDemo_NaiveFullCorpus\b/,
  /\bexperimental preset\b/i,
  /\bOPENAI_COMPATIBLE\b/,
  /\bOLLAMA_NATIVE\b/,
  /\bPromptBundleFingerprint\b/,
  /\bruntime prompt\b/i,
  /\binternal prompt\b/i,
];

/** Values may equal this exact title; bare "Technical details" without Advanced is forbidden. */
const FORBIDDEN_BARE_TECHNICAL_DETAILS = /^Technical details$/i;

const REQUIRED_ENGLISH_LABELS: Array<{ namespace: string; key: string; value: string }> = [
  { namespace: "Chat", key: "chatConfigPanelTitle", value: "Assistant configuration" },
  { namespace: "Chat", key: "configCompactTitle", value: "Configuration summary" },
  { namespace: "Chat", key: "chatTraceTechnicalSummary", value: ADVANCED_TECHNICAL_DETAILS_TITLE },
  { namespace: "Chat", key: "configAdvancedTechnicalSummary", value: ADVANCED_TECHNICAL_DETAILS_TITLE },
  { namespace: "Chat", key: "configTechnicalDetails", value: ADVANCED_TECHNICAL_DETAILS_TITLE },
  { namespace: "Settings", key: "assistantProfileSectionTitle", value: "Assistant instructions" },
  { namespace: "Chat", key: "configSectionModelConfiguration", value: "Model configuration" },
  { namespace: "Chat", key: "configSectionRetrievalSettings", value: "Retrieval" },
  { namespace: "Chat", key: "configSectionAssistant", value: "Assistant" },
  { namespace: "Chat", key: "configSectionModels", value: "Models" },
  { namespace: "Chat", key: "configSectionPrompts", value: "Prompts" },
  { namespace: "Chat", key: "configSectionMemoryAndClarification", value: "Memory and clarification" },
  { namespace: "Chat", key: "configSectionToolsAndQualityChecks", value: "Tools and quality checks" },
  { namespace: "Settings", key: "instructionsSystemLabel", value: "System instructions" },
  { namespace: "Settings", key: "instructionsPreviewTitle", value: "Preview configuration" },
  { namespace: "Settings", key: "configProviderOpenAiCompatible", value: "Configured model provider" },
  { namespace: "Settings", key: "configProviderOllamaNative", value: "Local model provider" },
  { namespace: "Chat", key: "chatMoreInformationLabel", value: "Answer quality checks" },
  { namespace: "Chat", key: "configSectionDocumentScope", value: "Source documents" },
];

describe("product UI language — en.json primary surfaces", () => {
  for (const ns of PRODUCT_NAMESPACES) {
    it(`${ns} namespace avoids forbidden internal identifiers in string values`, () => {
      const section = (en as Record<string, unknown>)[ns];
      const values = collectStringValues(section);
      for (const value of values) {
        if (value === ADVANCED_TECHNICAL_DETAILS_TITLE) continue;
        for (const pattern of FORBIDDEN_PRIMARY_COPY) {
          expect(value, `forbidden copy in ${ns}: ${value}`).not.toMatch(pattern);
        }
        expect(value, `bare technical details in ${ns}`).not.toMatch(FORBIDDEN_BARE_TECHNICAL_DETAILS);
      }
    });
  }

  it("includes required English product labels", () => {
    for (const { namespace, key, value } of REQUIRED_ENGLISH_LABELS) {
      const section = (en as unknown as Record<string, Record<string, string>>)[namespace];
      expect(section?.[key], `${namespace}.${key}`).toBe(value);
    }
  });

  it("Lab benchmark kind labels use product evaluation terminology", () => {
    const t = (key: string) => {
      const lab = en.Lab as Record<string, unknown>;
      const labels = lab.benchmarkKindLabel as Record<string, string>;
      const short = key.replace("benchmarkKindLabel.", "");
      return labels[short] ?? key;
    };
    expect(formatBenchmarkKindLabel("LLM_JUDGE_QA", t)).toBe("Chat model evaluation");
    expect(formatBenchmarkKindLabel("RAG_PRESET_END_TO_END", t)).toBe("Retrieval evaluation");
  });
});

describe("product UI language — preset display mapping", () => {
  it("maps seeded system preset codes to product configuration names", () => {
    expect(toProductPresetDisplayName("Demo_Best")).toBe("Production assistant configuration");
    expect(formatPresetDisplay("Demo_Best", "Demo_Best")).toBe("Production assistant configuration");
  });
});

/**
 * Spanish locale policy: new internal identifiers must not appear in es.json values.
 * Collapsed diagnostics title stays English per product policy (exact match).
 * Untranslated English fallbacks for new keys are acceptable until localized.
 */
describe("product UI language — es.json locale policy", () => {
  const ES_FORBIDDEN = [/\bDemo_Best\b/, /\bDemo_Worst\b/, /\bOPENAI_COMPATIBLE\b/, /\bOLLAMA_NATIVE\b/];

  it("does not expose raw internal preset or provider enum names", () => {
    const values = collectStringValues(es);
    for (const value of values) {
      for (const pattern of ES_FORBIDDEN) {
        expect(value).not.toMatch(pattern);
      }
    }
  });

  it("uses exact English title for Advanced technical details when present", () => {
    const chat = es.Chat as unknown as Record<string, string>;
    expect(chat.chatTraceTechnicalSummary).toBe(ADVANCED_TECHNICAL_DETAILS_TITLE);
    expect(chat.configTechnicalDetails).toBe(ADVANCED_TECHNICAL_DETAILS_TITLE);
  });

  it("documents locale file presence for fallback auditing", () => {
    const esRaw = readFileSync(join(messagesDir, "es.json"), "utf8");
    expect(esRaw.length).toBeGreaterThan(1000);
  });
});
