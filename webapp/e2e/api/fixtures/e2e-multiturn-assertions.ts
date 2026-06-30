import { expect } from "@playwright/test";
import type { MessageDto } from "./chat-runtime-api";

export const INTERNAL_STUB_RE =
  /Found \d+ relevant meeting minutes\.?|More information|Más información/i;

export const CLARIFICATION_DATE_RE =
  /¿\s*(?:podrías|puedes|podria)|especifica|qué acta|qué fecha|cuál acta|indica la fecha|fecha de la reunión|necesito.*fecha/i;

export const REASONING_LEAK_RE =
  /(?:^|\n)\s*(?:Thought:|Action:|Observation:|redacted_reasoning|redacted_thinking|<think)/i;

export type TurnAssertResult = {
  noInternalStub: boolean;
  noReasoningLeak: boolean;
  noUnnecessaryClarification: boolean;
  languageCoherent: boolean;
  contentExpectation: boolean;
  sourcesWhenApplicable: boolean;
};

export type TurnDefinition = {
  id: number;
  query: string;
  language: "es" | "en";
  requiresSources: boolean;
  allowClarification: boolean;
  contentMatchers: RegExp[];
};

export function assertTurnQuality(
  turn: TurnDefinition,
  assistant: MessageDto | undefined,
): TurnAssertResult {
  const answer = (assistant?.content ?? "").trim();
  const sources = Array.isArray(assistant?.sources) ? assistant!.sources! : [];

  const noInternalStub = !INTERNAL_STUB_RE.test(answer);
  const noReasoningLeak = !REASONING_LEAK_RE.test(answer);
  const noUnnecessaryClarification =
    turn.allowClarification || !CLARIFICATION_DATE_RE.test(answer);
  const languageCoherent = isLanguageCoherent(turn.language, answer);
  const contentExpectation =
    turn.contentMatchers.length === 0 || turn.contentMatchers.some((re) => re.test(answer));
  const sourcesWhenApplicable =
    !turn.requiresSources || sources.length > 0 || hasGroundingEvidence(assistant, answer);

  expect(answer.length, `turn ${turn.id} empty answer`).toBeGreaterThan(8);
  expect(answer, `turn ${turn.id} internal stub`).not.toMatch(INTERNAL_STUB_RE);
  expect(answer, `turn ${turn.id} reasoning leak`).not.toMatch(REASONING_LEAK_RE);
  if (!turn.allowClarification) {
    expect(answer, `turn ${turn.id} unnecessary clarification`).not.toMatch(CLARIFICATION_DATE_RE);
  }
  if (turn.requiresSources) {
    expect(
      sources.length > 0 || hasGroundingEvidence(assistant, answer),
      `turn ${turn.id} missing sources or grounding metadata`,
    ).toBe(true);
  }
  for (const re of turn.contentMatchers) {
    expect(answer, `turn ${turn.id} content ${re}`).toMatch(re);
  }
  if (turn.language === "en") {
    expect(languageCoherent, `turn ${turn.id} language`).toBe(true);
  }

  return {
    noInternalStub,
    noReasoningLeak,
    noUnnecessaryClarification,
    languageCoherent,
    contentExpectation,
    sourcesWhenApplicable,
  };
}

function isLanguageCoherent(expected: "es" | "en", answer: string): boolean {
  if (!answer.trim()) {
    return false;
  }
  if (expected === "es") {
    return true;
  }
  const hasLatin = /[A-Za-zÁÉÍÓÚáéíóúÑñ]/.test(answer);
  const mentionsQueryEntity =
    /\b(?:Luis|Ram[ií]rez|Ortega|elevator|ascensor|minutes|february|august|febrero|agosto)\b/i.test(
      answer,
    );
  if (mentionsQueryEntity) {
    return hasLatin;
  }
  const looksSpanishOnly =
    /\b(?:acta|reunión|presidente|secretaria|ascensor|aparece|documentos)\b/i.test(answer) &&
    !/\b(?:elevator|minutes|know about|dates of)\b/i.test(answer);
  return hasLatin && !looksSpanishOnly;
}

/** Deterministic-tool answers may omit `sources[]` while citing acta metadata in text or execution_metadata. */
function hasGroundingEvidence(assistant: MessageDto | undefined, answer: string): boolean {
  const meta = assistant?.executionMetadata ?? {};
  if (meta.finalAnswerSource === "TOOL_FINAL") {
    return true;
  }
  if (meta.workflowName === "deterministic-tool") {
    return true;
  }
  if (typeof meta.sourceCount === "number" && meta.sourceCount > 0) {
    return true;
  }
  if (meta.anchoredActaDate || meta.topSourceDocumentId) {
    return true;
  }
  return /ACTA\s+\d|\.pdf\b/i.test(answer);
}

export const RAW_CHUNK_LEAK_RE =
  /(?:^|\n)\s*(?:chunkId|documentId)\s*[:=]|CHUNK_LEVEL|\"retrieved_chunk_ids\"\s*:/i;

export const PROVIDER_MISMATCH_RE =
  /provider mismatch|OpenAI-compatible.*Ollama|Ollama.*OpenAI-compatible|model registry mismatch/i;

export const OLLAMA_ERROR_RE =
  /ollama.*(?:error|unavailable|connection refused)|pull the model|model .* not found/i;

export type GlobalAssertResult = TurnAssertResult & {
  noRawChunks: boolean;
  noProviderMismatch: boolean;
  noOllamaError: boolean;
  jobSucceeded: boolean;
};

export function assertGlobalTurnQuality(
  turn: TurnDefinition,
  assistant: MessageDto | undefined,
  jobStatus: string,
): GlobalAssertResult {
  const base = assertTurnQuality(turn, assistant);
  const answer = (assistant?.content ?? "").trim();

  const noRawChunks = !RAW_CHUNK_LEAK_RE.test(answer);
  const noProviderMismatch = !PROVIDER_MISMATCH_RE.test(answer);
  const noOllamaError = !OLLAMA_ERROR_RE.test(answer);
  const jobSucceeded = jobStatus === "SUCCEEDED";

  expect(answer, `turn ${turn.id} raw chunk leak`).not.toMatch(RAW_CHUNK_LEAK_RE);
  expect(answer, `turn ${turn.id} provider mismatch`).not.toMatch(PROVIDER_MISMATCH_RE);
  expect(answer, `turn ${turn.id} ollama error`).not.toMatch(OLLAMA_ERROR_RE);
  expect(jobStatus, `turn ${turn.id} job`).toBe("SUCCEEDED");

  return { ...base, noRawChunks, noProviderMismatch, noOllamaError, jobSucceeded };
}

/** Phase 5 — eight-case chat acceptance (exact user wording). */
export const EIGHT_CASE_USER_QUERIES = [
  "quienes fueron los asistentes del acta del 24 de febrero de 2025",
  "a qué hora empezó y a qué hora terminó esa acta?",
  "quién fue el presidente?",
  "y quién fue la secretaria?",
  "what do you know about Luis Ramírez Ortega?",
  "tell me the dates of the minutes where elevator issues are commented",
  "en cuántas actas aparece Rosa Aguilar Fernández",
  "hazme un resumen de los puntos tratados en el acta del 25 de agosto de 2025",
] as const;

export const MULTITURN_SUITE_TURNS: TurnDefinition[] = [
  {
    id: 1,
    query: "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/asistent|particip|20|veinte/i],
  },
  {
    id: 2,
    query: "¿A qué hora empezó y a qué hora terminó esa acta?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/19[:\s]?00/i, /20[:\s]?30/i],
  },
  {
    id: 3,
    query: "¿Quién fue el presidente?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/Juan\s+Pérez\s+Gutiérrez/i],
  },
  {
    id: 4,
    query: "¿Y quién fue la secretaria?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/Rosa\s+Aguilar\s+Fernández/i],
  },
  {
    id: 5,
    query: "What do you know about Luis Ramírez Ortega?",
    language: "en",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/Luis\s+Ram[ií]rez\s+Ortega/i],
  },
  {
    id: 6,
    query: "Tell me the dates of the minutes where elevator issues are commented",
    language: "en",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/elevator|ascensor/i, /\d{4}|febrero|agosto|february|august/i],
  },
  {
    id: 7,
    query: "¿En cuántas actas aparece Rosa Aguilar Fernández?",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/Rosa\s+Aguilar/i, /(?:en\s+)?(?:una|un|\d+|dos|tres|cuatro|cinco)\s+actas?/i],
  },
  {
    id: 8,
    query: "Hazme un resumen de los puntos tratados en el acta del 25 de agosto de 2025",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/25\s+de\s+agosto\s+de\s+2025|agosto\s+de\s+2025/i, /orden del día|tema|trat/i],
  },
];

export const EIGHT_CASE_ACCEPTANCE_TURNS: TurnDefinition[] = MULTITURN_SUITE_TURNS.map((turn, idx) => ({
  ...turn,
  query: EIGHT_CASE_USER_QUERIES[idx] ?? turn.query,
}));
