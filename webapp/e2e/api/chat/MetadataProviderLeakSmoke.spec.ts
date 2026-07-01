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
  fetchReadinessRagProvider,
  forbiddenProviderPatterns,
  scrapeBackendLogsSince,
} from "../fixtures/provider-runtime-acceptance";

const METADATA_QUERIES = [
  "dime las actas donde se comentan problemas del ascensor",
  "en cuántas actas aparece Rosa Aguilar Fernández",
] as const;

test.describe("Metadata provider leak smoke @api @metadata @phaseC", () => {
  test.describe.configure({ mode: "serial" });

  for (const query of METADATA_QUERIES) {
    test(`metadata query under OPENAI_COMPATIBLE: ${query.slice(0, 48)}`, async ({ request }) => {
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
        query,
        { pollTimeoutMs: 300_000 },
      );

      const answer = (assistant?.content ?? "").trim();
      const logs = scrapeBackendLogsSince(sinceIso);
      const logStats = analyzeOpenAiCompatibleLogs(logs);
      const forbidden = forbiddenProviderPatterns(logs);

      expect(job.status).toBe("SUCCEEDED");
      expect(answer.length).toBeGreaterThan(5);
      expect(answer).not.toMatch(OLLAMA_ERROR_RE);
      expect(answer).not.toMatch(PROVIDER_MISMATCH_RE);
      expect(logStats.ollamaPortHits, "no :11434 in LLM window").toBe(0);
      expect(forbidden.ollamaOptions).toBe(0);
      expect(forbidden.providerMismatch).toBe(0);
      expect(forbidden.fallbackToOllama).toBe(0);
    });
  }
});
