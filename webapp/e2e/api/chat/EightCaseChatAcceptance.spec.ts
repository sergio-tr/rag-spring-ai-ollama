import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_EXTENDED_FILES,
  ensureDemoBestConversation,
  getConversationMessages,
  postChatAndGetLatestAssistant,
  type MessageDto,
} from "../fixtures/chat-runtime-api";
import {
  assertGlobalTurnQuality,
  EIGHT_CASE_ACCEPTANCE_TURNS,
  type GlobalAssertResult,
} from "../fixtures/e2e-multiturn-assertions";
import { integrationCredentials } from "../fixtures/env";

const EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "../../../../docs/evidence/sprint-s4-fullstack-runtime-closure-20260628/05_eight_case_chat_acceptance",
);

type RecordedCase = {
  caseId: string;
  turnId: number;
  query: string;
  jobStatus: string;
  userMessageId: string | null;
  assistantMessageId: string | null;
  answer: string;
  sourceCount: number;
  executionMetadata: Record<string, unknown>;
  asserts: GlobalAssertResult;
  pass: boolean;
};

const GLOBAL_ASSERT_LABELS: Array<{ key: keyof GlobalAssertResult; label: string }> = [
  { key: "noInternalStub", label: "No stub (`More information` / `Found N relevant`)" },
  { key: "noReasoningLeak", label: "No reasoning leak" },
  { key: "noRawChunks", label: "No raw chunks in answer" },
  { key: "noProviderMismatch", label: "No provider mismatch" },
  { key: "noOllamaError", label: "No Ollama error under OpenAI-compatible" },
  { key: "jobSucceeded", label: "No uncontrolled timeout (job SUCCEEDED)" },
  { key: "sourcesWhenApplicable", label: "Sources when applicable" },
  { key: "languageCoherent", label: "Language coherent with question" },
  { key: "contentExpectation", label: "Content expectation met" },
  { key: "noUnnecessaryClarification", label: "No unnecessary clarification" },
];

function pickTraceMeta(assistant: MessageDto | undefined): Record<string, unknown> {
  const m = assistant?.executionMetadata ?? {};
  return {
    memoryOutcome: m.memoryOutcome ?? null,
    routingRouteKind: m.routingRouteKind ?? null,
    workflowName: m.workflowName ?? null,
    clarificationRequired: m.clarificationRequired ?? null,
    clarificationOutcome: m.clarificationOutcome ?? null,
    anchoredActaDate: m.anchoredActaDate ?? null,
    predictedQueryType: m.predictedQueryType ?? null,
    retrievalRoute: m.retrievalRoute ?? null,
    finalAnswerSource: m.finalAnswerSource ?? null,
    sourceCount: Array.isArray(assistant?.sources) ? assistant!.sources!.length : 0,
    metadataKeyCount: Object.keys(m).length,
  };
}

function writeEvidence(recorded: RecordedCase[], conversationId: string) {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  const allPass = recorded.every((r) => r.pass);
  const generatedAt = new Date().toISOString();

  const matrixLines = [
    "# Eight-case chat acceptance matrix",
    "",
    `**Generated:** ${generatedAt}`,
    `**Conversation:** \`${conversationId}\``,
    `**Stack:** Demo_Best + ACTA acceptance corpus`,
    `**Overall:** ${allPass ? "PASS" : "FAIL"}`,
    "",
    "## Global asserts (all cases)",
    "",
    "| # | Assert |",
    "|---|--------|",
    ...GLOBAL_ASSERT_LABELS.map((g, i) => `| ${i + 1} | ${g.label} |`),
    "",
    "## Per-case matrix",
    "",
    "| Case | Job | Stub | Reasoning | Chunks | Provider | Ollama | Timeout | Sources | Lang | Content | Pass |",
    "|------|-----|------|-----------|--------|----------|--------|---------|---------|------|---------|------|",
  ];

  for (const row of recorded) {
    const a = row.asserts;
    const yn = (v: boolean) => (v ? "✓" : "✗");
    matrixLines.push(
      `| ${row.caseId} | ${row.jobStatus} | ${yn(a.noInternalStub)} | ${yn(a.noReasoningLeak)} | ${yn(a.noRawChunks)} | ${yn(a.noProviderMismatch)} | ${yn(a.noOllamaError)} | ${yn(a.jobSucceeded)} | ${yn(a.sourcesWhenApplicable)} | ${yn(a.languageCoherent)} | ${yn(a.contentExpectation)} | ${row.pass ? "PASS" : "FAIL"} |`,
    );
  }

  matrixLines.push("", "## Queries", "");
  for (const row of recorded) {
    matrixLines.push(`- **${row.caseId}:** ${row.query}`);
  }
  matrixLines.push("", "FIN");
  fs.writeFileSync(path.join(EVIDENCE_DIR, "EIGHT_CASE_ACCEPTANCE_MATRIX.md"), matrixLines.join("\n"));

  const responseLines = [
    "# Eight-case chat acceptance - responses",
    "",
    `**Generated:** ${generatedAt}`,
    `**Conversation:** \`${conversationId}\``,
    "",
  ];
  for (const row of recorded) {
    responseLines.push(
      `## ${row.caseId}`,
      "",
      `**Query:** ${row.query}`,
      "",
      "```text",
      row.answer,
      "```",
      "",
      `**Sources:** ${row.sourceCount}`,
      "",
    );
  }
  responseLines.push("FIN");
  fs.writeFileSync(path.join(EVIDENCE_DIR, "RESPONSES.md"), responseLines.join("\n"));

  const traceLines = [
    "# Eight-case chat acceptance - traces",
    "",
    `**Generated:** ${generatedAt}`,
    `**Conversation:** \`${conversationId}\``,
    "",
  ];
  for (const row of recorded) {
    traceLines.push(
      `## ${row.caseId}`,
      "",
      `| Field | Value |`,
      `|-------|-------|`,
      `| userMessageId | \`${row.userMessageId ?? "-"}\` |`,
      `| assistantMessageId | \`${row.assistantMessageId ?? "-"}\` |`,
      `| jobStatus | ${row.jobStatus} |`,
      "",
      "### executionMetadata (subset)",
      "",
      "```json",
      JSON.stringify(row.executionMetadata, null, 2),
      "```",
      "",
    );
  }
  traceLines.push("FIN");
  fs.writeFileSync(path.join(EVIDENCE_DIR, "TRACES.md"), traceLines.join("\n"));

  fs.writeFileSync(
    path.join(EVIDENCE_DIR, "eight-case-result.json"),
    JSON.stringify({ conversationId, generatedAt, allPass, recorded }, null, 2),
  );
}

test.describe("Phase 5 eight-case chat acceptance @api @p0 @eightCase", () => {
  test.describe.configure({ mode: "serial" });

  test("runs C1–C8 against real stack and writes evidence", async ({ request }) => {
    test.setTimeout(1_200_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);

    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
    });

    const recorded: RecordedCase[] = [];

    async function chatTurn(turn: (typeof EIGHT_CASE_ACCEPTANCE_TURNS)[number]) {
      let lastError: unknown;
      for (let attempt = 1; attempt <= 2; attempt++) {
        try {
          const result = await postChatAndGetLatestAssistant(
            request,
            token,
            conversationId,
            turn.query,
            { pollTimeoutMs: 300_000 },
          );
          if (result.job.status === "SUCCEEDED" || attempt === 2) {
            return result;
          }
        } catch (e) {
          lastError = e;
          if (attempt === 2) {
            throw e;
          }
        }
      }
      throw lastError ?? new Error(`chat turn failed: ${turn.id}`);
    }

    for (const turn of EIGHT_CASE_ACCEPTANCE_TURNS) {
      const caseId = `C${turn.id}`;
      const { job, assistant } = await chatTurn(turn);

      const messages = await getConversationMessages(request, token, conversationId);
      const user = [...messages].reverse().find((m) => m.role === "USER" && m.content === turn.query);

      let asserts: GlobalAssertResult;
      let turnPass = true;
      try {
        asserts = assertGlobalTurnQuality(turn, assistant, job.status);
        turnPass = Object.values(asserts).every(Boolean);
      } catch {
        asserts = {
          noInternalStub: false,
          noReasoningLeak: false,
          noUnnecessaryClarification: false,
          languageCoherent: false,
          contentExpectation: false,
          sourcesWhenApplicable: false,
          noRawChunks: false,
          noProviderMismatch: false,
          noOllamaError: false,
          jobSucceeded: job.status === "SUCCEEDED",
        };
        turnPass = false;
      }

      recorded.push({
        caseId,
        turnId: turn.id,
        query: turn.query,
        jobStatus: job.status,
        userMessageId: user?.id ?? null,
        assistantMessageId: assistant?.id ?? null,
        answer: assistant?.content ?? "",
        sourceCount: Array.isArray(assistant?.sources) ? assistant!.sources!.length : 0,
        executionMetadata: pickTraceMeta(assistant),
        asserts,
        pass: turnPass,
      });
    }

    writeEvidence(recorded, conversationId);
    expect(recorded.every((r) => r.pass), JSON.stringify(recorded, null, 2)).toBe(true);
  });
});
