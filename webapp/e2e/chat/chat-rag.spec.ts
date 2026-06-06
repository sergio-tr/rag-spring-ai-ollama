import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  expandChatConfigurationRuntimeSection,
  loginAsSeedUser,
  openChatConfigurationPanel,
  productApiUrl,
  sendChatMessage,
  waitForDocumentReadyByName,
} from "../support/helpers";

/**
 * E2E-05: ingest a doc, then one chat turn.
 *
 * Demo-grade fullstack check: upload + READY document, chat turn, sources, runtime config, and trace/explainability UI.
 */
test.describe("Chat RAG", () => {
  test("E2E-05 upload, basic/advanced preset chat, sources, runtime and trace @fullstack @critical", async ({
    page,
  }) => {
    // Default Playwright test timeout is 30s; this flow waits up to 120s for READY indexing and 120s for the stub reply.
    // Without a higher cap, CI hits the global timeout mid-sendChatMessage and the page closes (flaky "composer recovery").
    test.setTimeout(240_000);

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-chat"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-sources.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("SOURCE_MARKER_E2E_05 retrieval smoke content.\n"),
    });
    await waitForDocumentReadyByName(page, "e2e-sources.txt", 120_000);

    const projectId = new URL(page.url()).searchParams.get("projectId");
    expect(projectId, `Expected projectId after document upload, got ${page.url()}`).toBeTruthy();
    await page.goto(`/en/chat?projectId=${projectId}`, { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/en\/chat/);
    await createNewChatConversation(page);

    await expect(page.getByTestId("chat-config-trigger")).toBeEnabled({ timeout: 30_000 });
    const panel = await openChatConfigurationPanel(page);
    const presetSelect = panel.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 15_000 });
    expect(await presetSelect.locator("option:not([disabled])").count()).toBeGreaterThan(0);
    await expandChatConfigurationRuntimeSection(panel);
    await expect(panel.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeVisible({
      timeout: 15_000,
    });
    await expect(panel.getByTestId("chat-config-runtime-refresh-effective")).toBeVisible({
      timeout: 15_000,
    });

    const optionValues = await presetSelect.locator("option").evaluateAll((options) =>
      options
        .map((o) => ({
          value: (o as HTMLOptionElement).value,
          text: (o.textContent ?? "").trim(),
          disabled: (o as HTMLOptionElement).disabled,
        }))
        .filter((o) => o.value && !o.disabled),
    );
    const advanced = optionValues.find((o) => /P9|P10|P11|P12|P13|P14|advanced|avanzad/i.test(o.text));
    if (advanced) {
      await presetSelect.selectOption(advanced.value);
      await expect(presetSelect).toHaveValue(advanced.value);
    }
    await page.keyboard.press("Escape").catch(() => undefined);

    await sendChatMessage(page, "What is in my project documents?");

    await expect(page.getByText(/could not send message/i)).toHaveCount(0);
    await expect(page.getByText(/E2E stub reply/i)).toBeVisible({ timeout: 120_000 });

    const url = new URL(page.url());
    const conversationId = url.searchParams.get("conversationId");
    expect(conversationId, `Expected conversationId in URL after send, got ${page.url()}`).toBeTruthy();
    const headers = await authHeadersFromPage(page);
    const messagesRes = await page.request.get(productApiUrl(`/conversations/${conversationId}/messages`), {
      headers,
    });
    expect(messagesRes.status(), await messagesRes.text()).toBe(200);
    const messages = (await messagesRes.json()) as Array<{
      role: string;
      content: string;
      sources?: unknown[];
      pipelineSteps?: unknown[];
      traceId?: string | null;
    }>;
    const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
    expect(assistant, "assistant message should be persisted").toBeTruthy();
    expect(assistant?.content ?? "").toContain("E2E stub reply");
    expect(Array.isArray(assistant?.sources) && assistant.sources.length > 0).toBe(true);
    expect(Array.isArray(assistant?.pipelineSteps) && assistant.pipelineSteps.length > 0).toBe(true);

    await expect(page.getByTestId("chat-sources")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("chat-answer")).toContainText(/E2E stub reply/i);

    await page.getByRole("button", { name: /open activity and tips|activity|actividad/i }).click();
    await expect(page.getByTestId("trace-history-list")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/message_submitted|assistant_processing_started|assistant_response_received/i).first())
      .toBeVisible({ timeout: 15_000 });
    const telemetry = page.getByTestId("explain-runtime-telemetry");
    if (await telemetry.isVisible().catch(() => false)) {
      await expect(telemetry).toContainText(/\w+/);
    }
  });
});
