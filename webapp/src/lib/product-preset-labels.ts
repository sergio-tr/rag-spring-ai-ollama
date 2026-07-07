export type PresetCopyFn = (key: string) => string;

/** Product-safe display names for seeded system presets (DB `name` stays internal). */
const SYSTEM_PRESET_DISPLAY: Record<string, string> = {
  demo_best: "Production assistant configuration",
  demo_worst: "Basic baseline configuration",
  demo_naivefullcorpus: "Full-context baseline",
};

/** Capability-aligned descriptions for seeded system presets (override stale DB copy). */
const SYSTEM_PRESET_DESCRIPTION: Record<string, string> = {
  demo_best:
    "Hybrid retrieval with expansion, metadata tools, function calling, advisor, and clarification. Memory, judge, and extended reasoning are off for interactive latency.",
  demo_worst: "Plain LLM only: no retrieval, tools, or query understanding.",
  demo_naivefullcorpus: "Full corpus injected into the prompt without semantic retrieval.",
};

/**
 * Capability-aligned fallback labels for experimental preset codes P0–P15.
 * i18n keys under `Chat.presetDisplay.*` take precedence when resolved.
 */
export const EXPERIMENTAL_PRESET_BUILTIN_DISPLAY: Record<string, string> = {
  P0: "Indexed text answers",
  P1: "Full-context in prompt",
  P2: "Document-level retrieval",
  P3: "Chunk-level retrieval",
  P4: "Chunk retrieval with metadata",
  P5: "Query understanding retrieval",
  P6: "Structured query rewriter",
  P7: "Retrieval with deterministic tools",
  P8: "Hybrid retrieval with reranking",
  P9: "Hybrid retrieval with function calling",
  P10: "Advisor-guided retrieval",
  P11: "Adaptive routing retrieval",
  P12: "Judge-enhanced retrieval",
  P13: "Clarification loop",
  P14: "Conversation memory",
  P15: "Integrated single-turn composition",
};

export const EXPERIMENTAL_PRESET_BUILTIN_DESCRIPTION: Record<string, string> = {
  P0: "Answers from indexed document text without dense retrieval.",
  P1: "Injects budgeted snapshot chunks into the prompt without dense retrieval.",
  P2: "Dense retrieval at document granularity.",
  P3: "Dense retrieval at chunk granularity.",
  P4: "Chunk-level retrieval enriched with document metadata.",
  P5: "Retrieval with query expansion only (no NER or structured rewrite).",
  P6: "Query expansion plus NER and structured rewrite; deterministic tools start at P7.",
  P7: "Chunk retrieval plus deterministic metadata tools.",
  P8: "Hybrid retrieval with ranker and post-retrieval processing.",
  P9: "Hybrid stack with backend function calling.",
  P10: "Hybrid retrieval with retrieval advisor packing.",
  P11: "Hybrid retrieval with adaptive route selection.",
  P12: "Hybrid retrieval with post-answer quality judge.",
  P13: "Multi-turn clarification before answering.",
  P14: "Multi-turn memory across conversation history.",
  P15: "Hybrid retrieval, backend function calling, and adaptive route composition.",
};

function normalizePresetCode(code: string): string {
  return code.trim().toUpperCase();
}

/** i18n keys exist only for experimental protocol codes (P0–P15), not product preset display names. */
function isExperimentalPresetCode(code: string): boolean {
  return /^P\d+$/.test(normalizePresetCode(code));
}

function isResolvedI18n(key: string, translated: string): boolean {
  if (!translated.trim()) return false;
  if (translated === key) return false;
  if (translated.includes(`Chat.${key}`) || translated.includes(`Lab.${key}`)) return false;
  return true;
}

/** Functional display label for a preset code or seeded system preset name. */
export function productPresetLabel(code: string, t?: PresetCopyFn): string {
  const trimmed = code.trim();
  if (!trimmed) return trimmed;

  const demoMapped = SYSTEM_PRESET_DISPLAY[trimmed.toLowerCase()];
  if (demoMapped) return demoMapped;

  const normalized = normalizePresetCode(trimmed);
  if (t && isExperimentalPresetCode(trimmed)) {
    const i18nKey = `presetDisplay.${normalized}`;
    const translated = t(i18nKey);
    if (isResolvedI18n(i18nKey, translated)) {
      return translated.trim();
    }
  }

  return EXPERIMENTAL_PRESET_BUILTIN_DISPLAY[normalized] ?? trimmed;
}

/** Short capability description for tooltips and advanced surfaces. */
export function productPresetDescription(code: string, t?: PresetCopyFn): string {
  const trimmed = code.trim();
  const demoMapped = SYSTEM_PRESET_DESCRIPTION[trimmed.toLowerCase()];
  if (demoMapped) {
    if (t) {
      const i18nKey = "presetDisplay.DEMO_BESTDescription";
      if (trimmed.toLowerCase() === "demo_best") {
        const translated = t(i18nKey);
        if (isResolvedI18n(i18nKey, translated)) {
          return translated.trim();
        }
      }
    }
    return demoMapped;
  }

  const normalized = normalizePresetCode(trimmed);
  if (t && isExperimentalPresetCode(trimmed)) {
    const i18nKey = `presetDisplay.${normalized}Description`;
    const translated = t(i18nKey);
    if (isResolvedI18n(i18nKey, translated)) {
      return translated.trim();
    }
  }
  return EXPERIMENTAL_PRESET_BUILTIN_DESCRIPTION[normalized] ?? "";
}

/** Secondary chip text for internal preset codes (not a primary label). */
export function productPresetInternalCodeChip(code: string): string | null {
  const normalized = normalizePresetCode(code);
  return /^P\d+$/.test(normalized) ? normalized : null;
}

export function toProductPresetDisplayName(name: string): string {
  return productPresetLabel(name);
}
