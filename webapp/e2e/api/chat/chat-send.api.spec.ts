import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import {
  createActivatedProjectAndConversation,
  patchConversation,
  postChatMessageAndPollTerminal,
  type ConversationDto,
} from "../fixtures/chat-runtime-api";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Chat send API @api @chatRuntime", () => {
  test("POST Buenos dias returns 202, poll never 500/502, conversation preset resolved", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { projectId, conversationId } = await createActivatedProjectAndConversation(request, token);

    const convProbe = await request.get(productUrl(`/projects/${projectId}/conversations`), {
      headers: authHeaders(token),
    });
    expect(convProbe.status(), await convProbe.text()).toBe(200);
    const convList = (await convProbe.json()) as ConversationDto[];
    const convBefore = convList.find((c) => c.id === conversationId);
    expect(convBefore, "conversation should be listed under project").toBeTruthy();
    const resolvedPreset =
      convBefore!.presetId ?? convBefore!.effectivePresetId ?? null;
    expect(resolvedPreset, "presetId or effectivePresetId should be present from API").toBeTruthy();
    expect(String(resolvedPreset).toLowerCase()).not.toBe("none");

    const terminal = await postChatMessageAndPollTerminal(request, token, conversationId, "Buenos dias", {
      pollTimeoutMs: 180_000,
    });
    expect(terminal.terminal).toBe(true);
    expect(["SUCCEEDED", "FAILED", "CANCELLED"]).toContain(terminal.status);

    const messagesRes = await request.get(productUrl(`/conversations/${conversationId}/messages`), {
      headers: authHeaders(token),
    });
    expect(messagesRes.status(), await messagesRes.text()).toBe(200);
    const messages = (await messagesRes.json()) as Array<{ role: string; content: string }>;
    expect(messages.some((m) => m.role === "USER" && m.content.includes("Buenos dias"))).toBe(true);
  });

  test("mismatched documentFilter: terminal FAILED exposes failureCode and JSON poll body stays non-HTML", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await createActivatedProjectAndConversation(request, token);

    await patchConversation(request, token, conversationId, {
      documentFilter: ["00000000-0000-4000-8000-000000000001"],
    });

    const terminal = await postChatMessageAndPollTerminal(request, token, conversationId, "Hello with bad scope", {
      pollTimeoutMs: 180_000,
    });
    expect(terminal.terminal).toBe(true);
    expect(terminal.status).toBe("FAILED");
    expect(terminal.failureCode).toBe("CHAT_DOCUMENT_SCOPE_EMPTY");
    expect((terminal.errorMessage ?? "").toLowerCase()).not.toContain("<html");
    expect(terminal.result?.phase).toBe("failed");
  });

  test("empty project: POST chat message avoids gateway 5xx", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await createActivatedProjectAndConversation(request, token);

    const terminal = await postChatMessageAndPollTerminal(request, token, conversationId, "Buenos dias", {
      pollTimeoutMs: 180_000,
    });
    expect(terminal.terminal).toBe(true);
  });
});
