import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_EXTENDED_FILES,
  ensureDemoBestConversation,
  postChatAndGetLatestAssistant,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials } from "../fixtures/env";
import {
  OLLAMA_ERROR_RE,
  PROVIDER_MISMATCH_RE,
} from "../fixtures/e2e-multiturn-assertions";
import {
  analyzeOpenAiCompatibleLogs,
  countSecondaryOps,
  countSecondaryOpsOpenAi,
  fetchReadinessRagProvider,
  forbiddenProviderPatterns,
  PHASE_C_LIVE_SMOKE_EVIDENCE_DIR,
  scrapeBackendLogsSince,
} from "../fixtures/provider-runtime-acceptance";

const REWRITE_QUERY =
  "tell me the dates of the minutes where elevator issues are commented";

const MULTITURN_TURNS = [
  "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
  "¿quién fue el presidente?",
  "y quién fue la secretaria?",
] as const;

type SmokeRecord = {
  rewrite: Record<string, unknown>;
  multiturn: Record<string, unknown>[];
  logAudit: Record<string, unknown>;
};

const smokeRecord: SmokeRecord = {
  rewrite: {},
  multiturn: [],
  logAudit: {},
};

function writeEvidenceMarkdown(): void {
  fs.mkdirSync(PHASE_C_LIVE_SMOKE_EVIDENCE_DIR, { recursive: true });
  const outPath = path.join(PHASE_C_LIVE_SMOKE_EVIDENCE_DIR, "live-smoke-result.json");
  let merged: SmokeRecord = { rewrite: {}, multiturn: [], logAudit: {} };
  if (fs.existsSync(outPath)) {
    try {
      merged = { ...merged, ...JSON.parse(fs.readFileSync(outPath, "utf8")) };
    } catch {
      /* fresh file */
    }
  }
  if (Object.keys(smokeRecord.rewrite).length > 0) {
    merged.rewrite = smokeRecord.rewrite;
  }
  if (smokeRecord.multiturn.length > 0) {
    merged.multiturn = smokeRecord.multiturn;
  }
  if (Object.keys(smokeRecord.logAudit).length > 0) {
    merged.logAudit = smokeRecord.logAudit;
  }
  fs.writeFileSync(outPath, JSON.stringify(merged, null, 2));
}

test.describe("Phase C gateway parity live smoke @api @p0 @phaseCGateway", () => {
  test.describe.configure({ mode: "serial" });

  test("rewrite path uses OPENAI_COMPATIBLE secondary executor", async ({ request }) => {
    test.setTimeout(420_000);
    const sinceIso = new Date().toISOString();
    const readiness = await fetchReadinessRagProvider(request);
    expect(readiness.details?.chatProvider).toBe("OPENAI_COMPATIBLE");

    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
    });

    const { job, assistant } = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      REWRITE_QUERY,
      { pollTimeoutMs: 300_000 },
    );

    const answer = (assistant?.content ?? "").trim();
    const logs = scrapeBackendLogsSince(sinceIso);
    const logStats = analyzeOpenAiCompatibleLogs(logs);
    const rewriteOps = countSecondaryOps(logs, "query-rewrite");
    const rewriteOpenAi = countSecondaryOpsOpenAi(logs, "query-rewrite");
    const forbidden = forbiddenProviderPatterns(logs);

    smokeRecord.rewrite = {
      sinceIso,
      conversationId,
      query: REWRITE_QUERY,
      jobStatus: job.status,
      answerPreview: answer.slice(0, 300),
      rewriteOps,
      rewriteOpenAi,
      logStats,
      forbidden,
    };

    expect(job.status).toBe("SUCCEEDED");
    expect(answer.length).toBeGreaterThan(10);
    expect(answer).not.toMatch(OLLAMA_ERROR_RE);
    expect(answer).not.toMatch(PROVIDER_MISMATCH_RE);
    expect(logStats.ollamaPortHits, "no :11434 in LLM window").toBe(0);
    expect(
      rewriteOps > 0 || rewriteOpenAi > 0,
      "expected query-rewrite secondary LLM log line",
    ).toBeTruthy();
    expect(logStats.ollamaPortHits, "no :11434 in LLM log window").toBe(0);
    expect(
      forbidden.secondaryLlmScoped ?? 0,
      "no :11434 in secondary LLM lines",
    ).toBe(0);
    expect(forbidden.providerMismatch).toBe(0);

    writeEvidenceMarkdown();
  });

  test("multiturn condense or deterministic follow-up under OPENAI_COMPATIBLE", async ({
    request,
  }) => {
    test.setTimeout(600_000);
    const sinceIso = new Date().toISOString();
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
    });

    for (const q of MULTITURN_TURNS) {
      const { job, assistant } = await postChatAndGetLatestAssistant(
        request,
        token,
        conversationId,
        q,
        { pollTimeoutMs: 300_000 },
      );
      const answer = (assistant?.content ?? "").trim();
      const meta = assistant?.executionMetadata ?? {};
      smokeRecord.multiturn.push({
        question: q,
        jobStatus: job.status,
        answerPreview: answer.slice(0, 300),
        effectivePlanningInputText:
          typeof meta.effectivePlanningInputText === "string"
            ? meta.effectivePlanningInputText.slice(0, 400)
            : null,
        memoryOutcome: meta.memoryOutcome ?? meta.conversationMemoryOutcome ?? null,
      });
      expect(job.status).toBe("SUCCEEDED");
      expect(answer).not.toMatch(OLLAMA_ERROR_RE);
      expect(answer).not.toMatch(PROVIDER_MISMATCH_RE);
    }

    const turn3 = smokeRecord.multiturn[2];
    const turn3Answer = String(turn3?.answerPreview ?? "");
    expect(turn3Answer).toMatch(/Rosa\s+Aguilar/i);

    const logs = scrapeBackendLogsSince(sinceIso);
    const condenseOps = countSecondaryOps(logs, "conversation-condense");
    const condenseOpenAi = countSecondaryOpsOpenAi(logs, "conversation-condense");
    const planningExpanded =
      typeof turn3?.effectivePlanningInputText === "string" &&
      turn3.effectivePlanningInputText.length > 20;
    const deterministicFollowUp = /Rosa\s+Aguilar/i.test(turn3Answer);

    smokeRecord.logAudit = {
      sinceIso,
      condenseOps,
      condenseOpenAi,
      planningExpanded,
      deterministicFollowUp,
      forbidden: forbiddenProviderPatterns(logs),
      ...analyzeOpenAiCompatibleLogs(logs),
    };

    expect(
      condenseOps > 0 || condenseOpenAi > 0 || planningExpanded || deterministicFollowUp,
      "condense LLM, planning expansion, or coherent follow-up answer required",
    ).toBeTruthy();

    writeEvidenceMarkdown();
  });
});
