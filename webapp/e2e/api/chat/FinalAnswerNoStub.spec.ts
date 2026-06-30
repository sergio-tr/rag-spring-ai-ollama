import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import {
  ensureDemoBestConversation,
  postChatAndGetLatestAssistant,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials } from "../fixtures/env";

const INTERNAL_STUB_RE =
  /Found \d+ relevant meeting minutes\.?|More information|Missing acta\/meeting date for scoped field lookup/i;

test.describe("P0 BL-004 final answer stub sanitizer @api @p0", () => {
  test("acta field answer is not an internal retrieval stub", async ({ request }) => {
    test.setTimeout(240_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const { job, assistant } = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "¿Quién fue el presidente en el acta del 25/02/2026?",
      { pollTimeoutMs: 180_000 },
    );

    expect(job.status).toBe("SUCCEEDED");
    const answer = assistant?.content ?? "";
    expect(answer.trim().length).toBeGreaterThan(10);
    expect(answer).not.toMatch(INTERNAL_STUB_RE);
  });
});
