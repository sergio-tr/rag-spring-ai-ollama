import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_FIXTURES_DIR,
  ensureDemoBestConversation,
  getConversationMessages,
  postChatAndGetLatestAssistant,
  type MessageDto,
} from "../fixtures/chat-runtime-api";
import {
  assertTurnQuality,
  MULTITURN_SUITE_TURNS,
  type TurnAssertResult,
} from "../fixtures/e2e-multiturn-assertions";
import { integrationCredentials } from "../fixtures/env";

/** Corpus aligned with evaluation actas (feb/aug 2025, elevator, Rosa, Luis). */
const MULTITURN_ACTA_FILES = [
  "ACTA 1.pdf",
  "ACTA 2.pdf",
  "ACTA 3.pdf",
  "ACTA 5.pdf",
  "ACTA 6.pdf",
] as const;

const EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "../../../..",
  "/docs/evidence/sprint-s2-answer-quality-evaluation-20260628/07_e2e_multiturn_suite",
);

type RecordedTurn = {
  id: number;
  query: string;
  jobStatus: string;
  answer: string;
  sourceCount: number;
  asserts: TurnAssertResult;
  pass: boolean;
};

const suiteResults: RecordedTurn[] = [];

function writeEvidenceMarkdown(recorded: RecordedTurn[], conversationId: string, corpus: string[]) {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  const allPass = recorded.every((r) => r.pass);
  const lines: string[] = [
    "# E2E Multiturn Memory Suite",
    "",
    `Generated: ${new Date().toISOString()}`,
    `Conversation: ${conversationId}`,
    `Corpus: ${corpus.join(", ")}`,
    `Overall: ${allPass ? "PASS" : "FAIL"}`,
    "",
    "## Minimum asserts",
    "",
    "- No `More information` / `Found N relevant` as final answer",
    "- No reasoning leak (`Thought:`, `redacted_thinking`, etc.)",
    "- Coherent language (EN queries answered in EN)",
    "- President / secretary grounded on acta 24/02/2025",
    "- No unnecessary date clarification on follow-ups",
    "- Sources when document-bound",
    "",
    "## Turn results",
    "",
    "| # | Status | Sources | Pass | Question |",
    "|---|--------|---------|------|----------|",
  ];
  for (const row of recorded) {
    const q = row.query.replace(/\|/g, "\\|").slice(0, 72);
    lines.push(
      `| ${row.id} | ${row.jobStatus} | ${row.sourceCount} | ${row.pass ? "PASS" : "FAIL"} | ${q} |`,
    );
  }
  lines.push("", "## Answers", "");
  for (const row of recorded) {
    lines.push(`### Turn ${row.id}`, "", "```text", row.answer.slice(0, 2000), "```", "");
  }
  lines.push("FIN");
  fs.writeFileSync(path.join(EVIDENCE_DIR, "E2E_MULTITURN_RESULT.md"), lines.join("\n"));
  fs.writeFileSync(
    path.join(EVIDENCE_DIR, "E2E_MULTITURN_RESULT.json"),
    JSON.stringify({ conversationId, corpus, recorded, allPass }, null, 2),
  );
}

test.describe("Phase 7 E2E multiturn memory suite @api @p0 @multiturn @critical", () => {
  test.describe.configure({ mode: "serial" });

  test("runs 8-turn closure suite and writes evidence", async ({ request }) => {
    test.setTimeout(900_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);

    for (const file of MULTITURN_ACTA_FILES) {
      const absolutePath = path.join(ACTA_ACCEPTANCE_FIXTURES_DIR, file);
      expect(fs.existsSync(absolutePath), `missing acta fixture ${absolutePath}`).toBe(true);
    }

    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: MULTITURN_ACTA_FILES,
    });

    for (const turn of MULTITURN_SUITE_TURNS) {
      const { job, assistant } = await postChatAndGetLatestAssistant(
        request,
        token,
        conversationId,
        turn.query,
        { pollTimeoutMs: 180_000 },
      );
      expect(job.status, `turn ${turn.id} job`).toBe("SUCCEEDED");

      let asserts: TurnAssertResult;
      try {
        asserts = assertTurnQuality(turn, assistant);
      } catch (e) {
        asserts = {
          noInternalStub: false,
          noReasoningLeak: false,
          noUnnecessaryClarification: false,
          languageCoherent: false,
          contentExpectation: false,
          sourcesWhenApplicable: false,
        };
        suiteResults.push({
          id: turn.id,
          query: turn.query,
          jobStatus: job.status,
          answer: assistant?.content ?? "",
          sourceCount: Array.isArray(assistant?.sources) ? assistant!.sources!.length : 0,
          asserts,
          pass: false,
        });
        throw e;
      }

      const pass = Object.values(asserts).every(Boolean);
      suiteResults.push({
        id: turn.id,
        query: turn.query,
        jobStatus: job.status,
        answer: assistant?.content ?? "",
        sourceCount: Array.isArray(assistant?.sources) ? assistant!.sources!.length : 0,
        asserts,
        pass,
      });
    }

    const messages = await getConversationMessages(request, token, conversationId);
    expect(messages.filter((m: MessageDto) => m.role === "USER").length).toBeGreaterThanOrEqual(8);

    writeEvidenceMarkdown(suiteResults, conversationId, [...MULTITURN_ACTA_FILES]);
    expect(suiteResults.every((r) => r.pass), JSON.stringify(suiteResults, null, 2)).toBe(true);
  });
});
