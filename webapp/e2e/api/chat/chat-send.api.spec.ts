import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { createActivatedProjectAndConversation, postChatMessageAndPollTerminal, type ConversationDto } from "../fixtures/chat-runtime-api";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Chat send API @api @chatRuntime", () => {
  test.describe.configure({ timeout: 240_000 });

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

  test("PATCH conversation rejects documentFilter id not in project with JSON 400, not HTML", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await createActivatedProjectAndConversation(request, token);

    const res = await request.patch(productUrl(`/conversations/${conversationId}`), {
      headers: {
        ...authHeaders(token),
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      data: { documentFilter: ["00000000-0000-4000-8000-000000000001"] },
    });
    const raw = await res.text();
    expect(res.status(), raw).toBe(400);
    const body = parseJsonExpectNonHtml(raw, "PATCH conversations documentFilter") as {
      success?: boolean;
      message?: string;
      code?: string;
    };
    expect(body.success).toBe(false);
    expect(String(body.message ?? "").toLowerCase()).toContain("not in project");
    expect(String(body.message ?? "").toLowerCase()).not.toContain("<html");
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
