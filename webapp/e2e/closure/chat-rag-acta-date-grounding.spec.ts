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

const EVIDENCE_DIR = path.resolve(__dirname, "../../../.cursor/evidence/wave-3-current/chat-rag");
const M5_EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/context/evidence/m5-chat-rag-quality-grounding",
);

test.describe("Closure Chat RAG acta date grounding @closure @fullstack @wave3", () => {
  test("acta exacta 24/02/2025: sources + exactDocumentMatch true + no pending/stop", async ({ page }) => {
    test.setTimeout(420_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.mkdirSync(path.join(M5_EVIDENCE_DIR, "messages"), { recursive: true });

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("w3-chat-acta"));

    // Upload the controlled fixture with a date in filename + content.
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
    // Prefer Demo_Best when present; otherwise fall back to any non-disabled preset.
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
      await expect(presetSelect).toHaveValue(pick);
    }
    await page.keyboard.press("Escape").catch(() => undefined);

    const q = "Quién fue el presidente del acta del 24 de febrero de 2025";
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "chat-acta-before.png"), fullPage: true });
    await sendChatMessage(page, q, { textareaReadyTimeoutMs: 30_000, sendEnabledTimeoutMs: 30_000 });

    // Must not stay in PENDING indefinitely: require final answer text and sources panel.
    await expect(page.getByTestId("chat-answer")).toBeVisible({ timeout: 180_000 });
    await expect(page.getByTestId("chat-sources")).toBeVisible({ timeout: 180_000 });
    await expect(page.getByText(/\[PENDING\]|Processing/i)).toHaveCount(0);

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "chat-acta-after.png"), fullPage: true });

    const conversationId = new URL(page.url()).searchParams.get("conversationId");
    expect(conversationId).toBeTruthy();

    const headers = await authHeadersFromPage(page);
    const messagesRes = await page.request.get(productApiUrl(`/conversations/${conversationId}/messages`), { headers });
    expect(messagesRes.status(), await messagesRes.text()).toBe(200);
    const messages = (await messagesRes.json()) as MessageDto[];
    const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
    expect(assistant, "assistant message should be persisted").toBeTruthy();

    // Contract: exactDocumentMatch true and acta file is used as source support.
    const exact = assistant?.executionMetadata?.exactDocumentMatch === true;
    expect(exact, JSON.stringify(assistant?.executionMetadata ?? {}, null, 2)).toBe(true);
    expect(String(assistant?.content ?? "")).toMatch(/Juan\s+Pérez/i);
    const sources = Array.isArray(assistant?.sources) ? assistant.sources : [];
    expect(sources.length).toBeGreaterThan(0);
    expect(JSON.stringify(sources)).toMatch(/acta-24-02-2025\.txt/i);

    const meta = assistant?.executionMetadata ?? {};
    fs.writeFileSync(path.join(EVIDENCE_DIR, "chat-acta-messages.json"), JSON.stringify(messages, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "chat-acta-assistant.json"), JSON.stringify(assistant, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "chat-acta-execution-metadata.json"), JSON.stringify(meta, null, 2));
    fs.writeFileSync(path.join(M5_EVIDENCE_DIR, "messages", "qc-01.json"), JSON.stringify(messages, null, 2));
  });
});

