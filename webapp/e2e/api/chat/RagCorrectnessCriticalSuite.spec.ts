import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test, type APIRequestContext } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_EXTENDED_FILES,
  ensureDemoBestConversation,
  postChatAndGetLatestAssistant,
  type MessageDto,
} from "../fixtures/chat-runtime-api";
import {
  assertRagCritTurnQuality,
  RAG_CRIT_CRITICAL_TURNS,
  type RagCritCaseDefinition,
} from "../fixtures/e2e-multiturn-assertions";
import { integrationCredentials } from "../fixtures/env";
import {
  analyzeOpenAiCompatibleLogs,
  forbiddenProviderPatterns,
  scrapeBackendLogsSince,
} from "../fixtures/provider-runtime-acceptance";

export const RAG_CRIT_EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "../../../../docs/evidence/rag-correctness-critical-suite-20260628",
);

type RecordedCritCase = {
  caseId: string;
  query: string;
  conversationId: string;
  jobStatus: string;
  answer: string;
  sourceCount: number;
  uniqueSourceFilenames: number;
  duplicateSources: boolean;
  executionMetadata: Record<string, unknown>;
  pass: boolean;
};

const recorded: RecordedCritCase[] = [];

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
    finalAnswerSource: m.finalAnswerSource ?? null,
    sourceCount: Array.isArray(assistant?.sources) ? assistant!.sources!.length : 0,
  };
}

function sourceFilenameStats(sources: unknown[]): { unique: number; duplicate: boolean } {
  const names: string[] = [];
  for (const row of sources) {
    if (!row || typeof row !== "object") continue;
    const rec = row as Record<string, unknown>;
    const name = String(rec.filename ?? rec.fileName ?? rec.documentId ?? "").trim().toLowerCase();
    if (name) names.push(name);
  }
  const unique = new Set(names).size;
  return { unique, duplicate: names.length > unique };
}

function writeEvidence(conversationIds: Record<string, string>, logAudit: Record<string, unknown>): void {
  fs.mkdirSync(RAG_CRIT_EVIDENCE_DIR, { recursive: true });
  const allPass = recorded.every((r) => r.pass);
  const generatedAt = new Date().toISOString();

  const matrixLines = [
    "# RAG Correctness Critical Suite - results",
    "",
    `**Generated:** ${generatedAt}`,
    `**Overall:** ${allPass ? "PASS" : "FAIL"}`,
    "",
    "| Case | Job | Sources | Dedup | Pass | Query |",
    "|------|-----|---------|-------|------|-------|",
  ];
  for (const row of recorded) {
    const q = row.query.replace(/\|/g, "\\|").slice(0, 56);
    matrixLines.push(
      `| ${row.caseId} | ${row.jobStatus} | ${row.sourceCount} | ${row.duplicateSources ? "DUP" : "OK"} | ${row.pass ? "PASS" : "FAIL"} | ${q} |`,
    );
  }
  matrixLines.push("", "FIN");
  fs.writeFileSync(path.join(RAG_CRIT_EVIDENCE_DIR, "suite-result-matrix.md"), matrixLines.join("\n"));

  fs.writeFileSync(
    path.join(RAG_CRIT_EVIDENCE_DIR, "suite-result.json"),
    JSON.stringify({ generatedAt, allPass, conversationIds, recorded, logAudit }, null, 2),
  );
}

async function runCritCase(
  request: APIRequestContext,
  token: string,
  conversationId: string,
  turn: RagCritCaseDefinition,
): Promise<RecordedCritCase> {
  const { job, assistant } = await postChatAndGetLatestAssistant(
    request,
    token,
    conversationId,
    turn.query,
    { pollTimeoutMs: 360_000 },
  );
  const sources = Array.isArray(assistant?.sources) ? assistant!.sources! : [];
  const stats = sourceFilenameStats(sources);
  let pass = true;
  try {
    assertRagCritTurnQuality(turn, assistant, job.status);
  } catch {
    pass = false;
  }
  const row: RecordedCritCase = {
    caseId: turn.caseId,
    query: turn.query,
    conversationId,
    jobStatus: job.status,
    answer: assistant?.content ?? "",
    sourceCount: sources.length,
    uniqueSourceFilenames: stats.unique,
    duplicateSources: stats.duplicate,
    executionMetadata: pickTraceMeta(assistant),
    pass,
  };
  recorded.push(row);
  expect(pass, JSON.stringify(row, null, 2)).toBe(true);
  return row;
}

test.describe("RAG Correctness Critical Suite @api @p0 @ragCrit", () => {
  test.describe.configure({ mode: "serial" });

  test("runs RAG-CRIT-001..012 against live stack", async ({ request }) => {
    test.setTimeout(3_600_000);
    const sinceIso = new Date().toISOString();
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);

    const conversationIds: Record<string, string> = {};
    let followUpConversationId: string | null = null;

    for (const turn of RAG_CRIT_CRITICAL_TURNS) {
      if (turn.caseId === "RAG-CRIT-008") {
        expect(followUpConversationId, "RAG-CRIT-007 must run before 008").toBeTruthy();
        await runCritCase(request, token, followUpConversationId!, turn);
        continue;
      }

      const needsSharedFollowUp = turn.caseId === "RAG-CRIT-007";
      const { conversationId } = await ensureDemoBestConversation(request, token, {
        actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
      });
      conversationIds[turn.caseId] = conversationId;
      if (needsSharedFollowUp) {
        followUpConversationId = conversationId;
      }
      await runCritCase(request, token, conversationId, turn);
    }

    const logs = scrapeBackendLogsSince(sinceIso);
    const logAudit = {
      sinceIso,
      ...analyzeOpenAiCompatibleLogs(logs),
      forbidden: forbiddenProviderPatterns(logs),
    };

    writeEvidence(conversationIds, logAudit);

    expect(recorded).toHaveLength(12);
    expect(recorded.every((r) => r.pass)).toBe(true);
    expect(logAudit.forbidden?.providerMismatch ?? 0).toBe(0);
  });
});
