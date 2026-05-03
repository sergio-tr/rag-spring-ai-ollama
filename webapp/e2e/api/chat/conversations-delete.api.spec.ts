import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { createActivatedProjectAndConversation } from "../fixtures/chat-runtime-api";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Conversation delete API @api", () => {
  test("DELETE conversation returns 204 and subsequent GET messages stays JSON-safe @api", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await createActivatedProjectAndConversation(request, token);

    const del = await request.delete(productUrl(`/conversations/${conversationId}`), {
      headers: authHeaders(token),
    });
    expect(del.status()).toBe(204);
    expect((await del.text()).trim()).toBe("");

    const probe = await request.get(productUrl(`/conversations/${conversationId}/messages`), {
      headers: authHeaders(token),
    });
    expect([401, 403, 404]).toContain(probe.status());
    const probeText = await probe.text();
    if (probeText.trim().length > 0) {
      parseJsonExpectNonHtml(probeText, "GET messages after delete");
    }
  });
});
