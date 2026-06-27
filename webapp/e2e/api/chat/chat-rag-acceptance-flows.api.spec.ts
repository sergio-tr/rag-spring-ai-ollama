import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import {
  DEMO_BEST_PRESET_ID,
  ensureDemoBestConversation,
  getChatPresetCatalog,
  getConversationMessages,
  postChatAndGetLatestAssistant,
  postChatMessageAndPollTerminal,
} from "../fixtures/chat-runtime-api";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

const CLARIFICATION_RE =
  /¿|especifica|qué acta|qué fecha|cuál|ambig|reunión|refieres|indica la fecha/i;
const NEGATIVE_EVIDENCE_RE =
  /no se encuentra ninguna mención|no se comentó respecto a la fuga de gas/i;

test.describe("Chat RAG acceptance flows API @api @chatAcceptance", () => {
  test.describe.configure({ mode: "serial" });

  test("GET chat/presets/catalog exposes Demo_Best and PATCH conversation selects it", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const catalog = await getChatPresetCatalog(request, token);
    const demoBest = catalog.productPresets.find((p) => p.name === "Demo_Best");
    expect(demoBest?.id).toBe(DEMO_BEST_PRESET_ID);

    const { projectId, conversationId, demoBestPresetId } = await ensureDemoBestConversation(
      request,
      token,
    );
    expect(demoBestPresetId).toBe(DEMO_BEST_PRESET_ID);

    const convRes = await request.get(productUrl(`/projects/${projectId}/conversations`), {
      headers: authHeaders(token),
    });
    expect(convRes.status(), await convRes.text()).toBe(200);
    const convList = (await convRes.json()) as Array<{
      id: string;
      presetId?: string | null;
      effectivePresetId?: string | null;
    }>;
    const conv = convList.find((c) => c.id === conversationId);
    expect(conv).toBeTruthy();
    const resolved = conv!.presetId ?? conv!.effectivePresetId;
    expect(resolved).toBe(DEMO_BEST_PRESET_ID);
  });

  test("Demo_Best: deterministic metadata answer persists via GET messages", async ({ request }) => {
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
    expect(assistant, "assistant message persisted").toBeTruthy();
    expect(String(assistant!.content)).toMatch(/Jorge\s+Moreno\s+Navarro/i);

    const messages = await getConversationMessages(request, token, conversationId);
    expect(messages.some((m) => m.role === "USER" && m.content.includes("25/02/2026"))).toBe(true);
    expect(messages.some((m) => m.role === "ASSISTANT" && /Jorge/i.test(m.content))).toBe(true);
  });

  test("Demo_Best: clarification response for undated count query", async ({ request }) => {
    test.setTimeout(240_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const { job, assistant } = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "¿Cuántos participantes asistieron?",
      { pollTimeoutMs: 180_000 },
    );
    expect(job.status).toBe("SUCCEEDED");
    expect(assistant?.content ?? "").toMatch(CLARIFICATION_RE);
  });

  test("Demo_Best: negative-evidence answer for gas-leak topic absence", async ({ request }) => {
    test.setTimeout(240_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const { job, assistant } = await postChatAndGetLatestAssistant(
      request,
      token,
      conversationId,
      "¿Qué se comentó respecto a la fuga de gas?",
      { pollTimeoutMs: 180_000 },
    );
    expect(job.status).toBe("SUCCEEDED");
    expect(assistant?.content ?? "").toMatch(NEGATIVE_EVIDENCE_RE);
  });

  test("Demo_Best: GET lab/jobs returns terminal persisted job after chat", async ({ request }) => {
    test.setTimeout(240_000);
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const job = await postChatMessageAndPollTerminal(
      request,
      token,
      conversationId,
      "¿Cuántas actas mencionan el ascensor?",
      { pollTimeoutMs: 180_000 },
    );
    expect(job.terminal).toBe(true);
    expect(job.status).toBe("SUCCEEDED");

    const pollRes = await request.get(productUrl(`/lab/jobs/${job.id}`), {
      headers: authHeaders(token),
    });
    expect(pollRes.status(), await pollRes.text()).toBe(200);
    const persisted = (await pollRes.json()) as typeof job;
    expect(persisted.id).toBe(job.id);
    expect(persisted.terminal).toBe(true);
    expect(persisted.status).toBe("SUCCEEDED");
  });

  test("POST chat with blank content returns JSON 400", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const { conversationId } = await ensureDemoBestConversation(request, token);

    const res = await request.post(productUrl(`/conversations/${conversationId}/messages`), {
      headers: { ...authHeaders(token), "Content-Type": "application/json", Accept: "application/json" },
      data: { content: "   ", llmModel: null },
    });
    const raw = await res.text();
    expect(res.status(), raw).toBe(400);
    const body = parseJsonExpectNonHtml(raw, "POST blank message") as {
      success?: boolean;
      message?: string;
      error?: { code?: string; message?: string };
    };
    expect(body.success).toBe(false);
    expect(String(body.message ?? body.error?.message ?? "").toLowerCase()).toContain("content");
  });
});
