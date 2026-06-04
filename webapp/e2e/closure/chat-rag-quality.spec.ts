import { expect, test } from "@playwright/test";
import * as path from "node:path";
import * as fs from "node:fs";
import { uniqueProjectName } from "../fixtures/projects";
import { fixtureFilesDir } from "../fixtures/documents";
import type { MessageDto } from "@/types/api";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  openChatConfigurationPanel,
  openChatForProject,
  productApiUrl,
  sendChatMessage,
  waitForDocumentReadyByName,
} from "../support/helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/context/evidence/m5-chat-rag-quality-grounding",
);

test.describe("Closure Chat RAG quality @closure @fullstack @m5", () => {
  test("T-M5-E2E-quality: wrong-date acta abstains without invented president", async ({ page }) => {
    test.setTimeout(420_000);
    fs.mkdirSync(path.join(EVIDENCE_DIR, "messages"), { recursive: true });

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("m5-chat-qc02"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    const actaPath = path.join(fixtureFilesDir(), "acta-24-02-2025.txt");
    await page.locator('input[type="file"]').setInputFiles(actaPath);
    await waitForDocumentReadyByName(page, "acta-24-02-2025.txt", 180_000);

    const projectId = new URL(page.url()).searchParams.get("projectId");
    expect(projectId).toBeTruthy();

    await openChatForProject(page, projectId!);
    await createNewChatConversation(page, { projectId: projectId! });

    const panel = await openChatConfigurationPanel(page);
    const presetSelect = panel.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 15_000 });
    const options = await presetSelect.locator("option").evaluateAll((opts) =>
      opts.map((o) => ({
        value: (o as HTMLOptionElement).value,
        text: (o.textContent ?? "").trim(),
        disabled: (o as HTMLOptionElement).disabled,
      })),
    );
    const demoBest = options.find((o) => !o.disabled && /demo_best/i.test(o.text));
    const firstEnabled = options.find((o) => !o.disabled && o.value);
    const pick = demoBest?.value ?? firstEnabled?.value;
    if (pick) {
      await presetSelect.selectOption(pick);
    }
    await page.keyboard.press("Escape").catch(() => undefined);

    const q = "¿Quién presidió el acta del 15 de marzo de 2099?";
    await sendChatMessage(page, q, { textareaReadyTimeoutMs: 30_000, sendEnabledTimeoutMs: 30_000 });

    await expect(page.getByTestId("chat-answer")).toBeVisible({ timeout: 180_000 });
    await expect(page.getByText(/\[PENDING\]|Processing/i)).toHaveCount(0);

    const conversationId = new URL(page.url()).searchParams.get("conversationId");
    expect(conversationId).toBeTruthy();

    const headers = await authHeadersFromPage(page);
    const messagesRes = await page.request.get(productApiUrl(`/conversations/${conversationId}/messages`), {
      headers,
    });
    expect(messagesRes.status(), await messagesRes.text()).toBe(200);
    const messages = (await messagesRes.json()) as MessageDto[];
    const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
    expect(assistant, "assistant message should be persisted").toBeTruthy();
    fs.writeFileSync(path.join(EVIDENCE_DIR, "messages", "qc-02.json"), JSON.stringify(messages, null, 2));

    const answerText = String(assistant?.content ?? (await page.getByTestId("chat-answer").innerText()));
    expect(answerText).not.toMatch(/Juan\s+Pérez/i);

    const meta = assistant?.executionMetadata ?? {};
    const mismatch =
      meta.dateMismatchDetected === true
      || meta.abstentionReason === "no_exact_date_source"
      || /2099|no he encontrado|no encuentro|no consta/i.test(answerText);
    expect(mismatch, JSON.stringify(meta, null, 2)).toBeTruthy();
  });
});
