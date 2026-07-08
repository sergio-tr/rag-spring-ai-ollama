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

/** Phase 5 - eight-case chat acceptance (exact user wording). */
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

/** RAG Correctness Critical Suite - mandatory acceptance cases (RAG-CRIT-001..012). */
export type RagCritCaseDefinition = TurnDefinition & {
  caseId: string;
  /** When set, run in the same conversation immediately after the referenced case. */
  followsCaseId?: string;
  /** Optional patterns the answer must not match. */
  forbiddenMatchers?: RegExp[];
};

export const RAG_CRIT_CRITICAL_TURNS: RagCritCaseDefinition[] = [
  {
    caseId: "RAG-CRIT-001",
    id: 1,
    query: "¿Cuáles fueron los puntos del día?",
    language: "es",
    requiresSources: false,
    allowClarification: true,
    contentMatchers: [
      /¿|podrías|puedes|especifica|qué acta|qué fecha|cuál acta|indica la fecha|fecha de la reunión|necesito.*fecha|varias|múltiples|diferentes actas|orden del día|encontraron.*actas|actas con las fechas|fechas:|\d{2}\/\d{2}\/\d{4}|lectura.*aprobación|aprobación del acta/i,
    ],
    forbiddenMatchers: [/radiación solar/i],
  },
  {
    caseId: "RAG-CRIT-002",
    id: 2,
    query: "¿Cuáles fueron los puntos del día del acta del 25 de febrero de 2026?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [
      /25.*02.*2026|febrero.*2026/i,
      /convivencia|calefacción|calefaccion|reparaciones|ruegos|lectura|orden del día|orden del dia/i,
    ],
  },
  {
    caseId: "RAG-CRIT-003",
    id: 3,
    query: "dime las fechas de las actas que terminaron más tarde de las 8:30",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/20:30|más tarde|mas tarde/i],
  },
  {
    caseId: "RAG-CRIT-004",
    id: 4,
    query: "dime las actas donde se comentan problemas del ascensor",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/ascensor/i, /ACTA\s*1|24.*02.*2025/i],
  },
  {
    caseId: "RAG-CRIT-005",
    id: 5,
    query: "tell me the dates of the minutes where elevator issues are commented",
    language: "en",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/elevator|ascensor/i, /\d{4}|february|august|febrero|agosto/i],
  },
  {
    caseId: "RAG-CRIT-006",
    id: 6,
    query: "en cuántas actas aparece Rosa Aguilar Fernández",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/Rosa\s+Aguilar/i, /(?:en\s+)?(?:una|un|\d+|dos|tres|cuatro|cinco)\s+actas?/i],
  },
  {
    caseId: "RAG-CRIT-007",
    id: 7,
    query: "¿Quién fue el presidente del acta del 24 de febrero de 2025?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/Juan\s+Pérez\s+Gutiérrez/i],
  },
  {
    caseId: "RAG-CRIT-008",
    id: 8,
    query: "¿Y la secretaria?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    followsCaseId: "RAG-CRIT-007",
    contentMatchers: [/Rosa\s+Aguilar\s+Fernández/i],
  },
  {
    caseId: "RAG-CRIT-009",
    id: 9,
    query: "¿Se habló de radiación solar?",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/no|sin evidencia|no se encontr|no consta|no hay|ningún|ninguna/i],
    forbiddenMatchers: [/sí,?\s+se habló.*radiación solar/i],
  },
  {
    caseId: "RAG-CRIT-010",
    id: 10,
    query: "Resume el acta del 25 de febrero de 2026.",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/25.*02.*2026|febrero.*2026/i, /17|diecisiete|asistent|19[:\s]?00|20[:\s]?30/i],
    forbiddenMatchers: [/25 de agosto de 2026/i],
  },
  {
    caseId: "RAG-CRIT-011",
    id: 11,
    query: "Número de actas registradas en el año 2028.",
    language: "es",
    requiresSources: false,
    allowClarification: false,
    contentMatchers: [/2028/i, /no exist|no hay|no se encontr|ninguna|ningún|cero|0 actas/i],
    forbiddenMatchers: [/Se encontraron 2028 actas/i],
  },
  {
    caseId: "RAG-CRIT-012",
    id: 12,
    query: "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
    language: "es",
    requiresSources: true,
    allowClarification: false,
    contentMatchers: [/asistent|particip|20|veinte/i],
  },
];

/** Principal source filenames must not duplicate in chat `sources[]`. */
export function assertNoDuplicateSourceFilenames(sources: unknown[]): boolean {
  if (!Array.isArray(sources) || sources.length <= 1) {
    return true;
  }
  const names: string[] = [];
  for (const row of sources) {
    if (!row || typeof row !== "object") continue;
    const rec = row as Record<string, unknown>;
    const name = String(rec.filename ?? rec.fileName ?? rec.documentId ?? "").trim().toLowerCase();
    if (!name) continue;
    if (names.includes(name)) return false;
    names.push(name);
  }
  return true;
}

export function assertRagCritTurnQuality(
  turn: RagCritCaseDefinition,
  assistant: MessageDto | undefined,
  jobStatus: string,
): GlobalAssertResult {
  const base = assertGlobalTurnQuality(turn, assistant, jobStatus);
  const answer = (assistant?.content ?? "").trim();
  for (const re of turn.forbiddenMatchers ?? []) {
    expect(answer, `${turn.caseId} forbidden ${re}`).not.toMatch(re);
  }
  if (turn.caseId === "RAG-CRIT-003") {
    const dateHits = ["25/02/2025", "25/08/2025", "25/08/2026"].filter((d) => answer.includes(d));
    expect(dateHits.length, `${turn.caseId} expected ≥2 end-after-20:30 dates`).toBeGreaterThanOrEqual(2);
  }
  if (turn.caseId === "RAG-CRIT-012") {
    const sources = Array.isArray(assistant?.sources) ? assistant!.sources! : [];
    expect(
      assertNoDuplicateSourceFilenames(sources),
      `${turn.caseId} duplicate source filenames`,
    ).toBe(true);
  }
  return base;
}
