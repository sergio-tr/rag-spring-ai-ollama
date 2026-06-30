import { expect, test } from "@playwright/test";
import { loginAndGetToken } from "../fixtures/auth";
import {
  ensureDemoBestConversation,
  postChatAndGetLatestAssistant,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials } from "../fixtures/env";

test.describe("P0 BL-002 sparse retrieval time token @api @p0", () => {
  test("query with 8:30 completes without server error", async ({ request }) => {
    test.setTimeout(240_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const q =
      "dime las fechas de las actas que terminaron más tarde de las 8:30";
    const { job, assistant } = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      q,
      { pollTimeoutMs: 180_000 },
    );

    expect(job.status).toBe("SUCCEEDED");
    expect(job.failureCode ?? "").not.toMatch(/INTERNAL|SPARSE|TSQUERY/i);
    expect(assistant?.content ?? "").not.toMatch(/502 Bad Gateway|504 Gateway|internal server error/i);
  });
});
