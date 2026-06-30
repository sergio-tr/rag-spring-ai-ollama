import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ACTA_ACCEPTANCE_EXTENDED_FILES,
  ensureDemoBestConversation,
  getConversationMessages,
  postChatAndGetLatestAssistant,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials } from "../fixtures/env";

const CLARIFICATION_DATE_RE =
  /¿|especifica|qué acta|qué fecha|cuál|indica la fecha|fecha de la/i;
const STUB_RE = /Found \d+ relevant meeting minutes|More information/i;

test.describe("P0 conversation memory follow-up @api @p0 @critical", () => {
  test.describe.configure({ mode: "serial" });

  test("president and secretary follow-ups do not ask for date when acta was anchored", async ({
    request,
  }) => {
    test.setTimeout(420_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token, {
      actaFixtureFiles: ACTA_ACCEPTANCE_EXTENDED_FILES,
    });

    const anchorQ =
      "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?";
    const anchor = await postChatAndGetLatestAssistant(request, token, conversationId, anchorQ, {
      pollTimeoutMs: 180_000,
    });
    expect(anchor.job.status).toBe("SUCCEEDED");
    expect(anchor.assistant?.content ?? "").not.toMatch(STUB_RE);

    const president = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "¿quién fue el presidente?",
      { pollTimeoutMs: 180_000 },
    );
    expect(president.job.status).toBe("SUCCEEDED");
    expect(president.assistant?.content ?? "").not.toMatch(CLARIFICATION_DATE_RE);
    expect(president.assistant?.content ?? "").not.toMatch(STUB_RE);
    expect(president.assistant?.content ?? "").toMatch(/Juan Pérez Gutiérrez/i);

    const secretary = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "y quién fue la secretaria?",
      { pollTimeoutMs: 180_000 },
    );
    expect(secretary.job.status).toBe("SUCCEEDED");
    expect(secretary.assistant?.content ?? "").not.toMatch(CLARIFICATION_DATE_RE);
    expect(secretary.assistant?.content ?? "").toMatch(/Rosa Aguilar Fernández/i);

    const times = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "¿a qué hora empezó y a qué hora terminó esa acta?",
      { pollTimeoutMs: 180_000 },
    );
    expect(times.job.status).toBe("SUCCEEDED");
    expect(times.assistant?.content ?? "").not.toMatch(CLARIFICATION_DATE_RE);
    expect(times.assistant?.content ?? "").toMatch(/19:00/);
    expect(times.assistant?.content ?? "").toMatch(/20:30/);

    const messages = await getConversationMessages(request, token, conversationId);
    expect(messages.filter((m) => m.role === "USER").length).toBeGreaterThanOrEqual(4);
  });
});
