import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_EXTENDED_FILES,
  ensureDemoBestConversation,
  getConversationMessages,
  postChatAndGetLatestAssistant,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials } from "../fixtures/env";
import * as fs from "node:fs";
import * as path from "node:path";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/evidence/sprint-s4-fullstack-runtime-closure-20260628/04_memory_runtime_closure",
);

const TURNS = [
  "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
  "¿quién fue el presidente?",
  "y quién fue la secretaria?",
  "¿a qué hora empezó y a qué hora terminó esa acta?",
];

test.describe("Runtime trace @api @trace", () => {
  test("capture execution metadata for 4-turn acta anchor flow", async ({ request }) => {
    test.setTimeout(420_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
    });

    const turns: unknown[] = [];
    const trace = { conversationId, turns };

    for (const q of TURNS) {
      const { job, assistant } = await postChatAndGetLatestAssistant(request, token, conversationId, q, {
        pollTimeoutMs: 180_000,
      });
      const messages = await getConversationMessages(request, token, conversationId);
      const user = [...messages].reverse().find((m) => m.role === "USER" && m.content === q);
      const meta = assistant?.executionMetadata ?? {};
      turns.push({
        question: q,
        userMessageId: user?.id ?? null,
        assistantMessageId: assistant?.id ?? null,
        jobStatus: job.status,
        assistantPreview: (assistant?.content ?? "").slice(0, 300),
        anchoredActaDate: meta.anchoredActaDate ?? null,
        effectivePlanningInputText: meta.effectivePlanningInputText ?? null,
        pendingClarification: meta.pendingClarification ?? null,
        selectedRoute: meta.selectedRoute ?? meta.adaptiveRouteKind ?? null,
        guardDecision: meta.guardDecision ?? meta.recallGuardDecision ?? null,
        memoryOutcome: meta.memoryOutcome ?? meta.conversationMemoryOutcome ?? null,
        executionMetadataKeys: Object.keys(meta),
      });
      expect(job.status).toBe("SUCCEEDED");
    }

    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(path.join(EVIDENCE_DIR, "runtime-trace.json"), JSON.stringify(trace, null, 2));
  });
});
