import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { uniqueProjectName } from "../fixtures/projects";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  openChatForProject,
  productApiUrl,
  sendChatMessage,
} from "../support/helpers";

const EVIDENCE_DIR = path.resolve(__dirname, "../../../.docs/evidence/wave-3-current/message-order");

type ApiMessage = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  seq?: number;
  createdAt: string;
};

async function fetchMessages(page: import("@playwright/test").Page, conversationId: string): Promise<ApiMessage[]> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl(`/conversations/${conversationId}/messages`), { headers });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as ApiMessage[];
}

test.describe("Closure Chat message edit order @closure @fullstack @wave3", () => {
  test("edited user message keeps position; assistant stays below; no duplicate user rows", async ({ page }) => {
    test.setTimeout(420_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });

    const original = "Pregunta original E2E orden mensajes";
    const edited = "Pregunta editada E2E orden mensajes";

    await loginAsSeedUser(page);
    const projectId = await createAndActivateProject(page, uniqueProjectName("w3-msg-order"));
    await openChatForProject(page, projectId);
    await createNewChatConversation(page, { projectId });

    await sendChatMessage(page, original, { textareaReadyTimeoutMs: 30_000, sendEnabledTimeoutMs: 30_000 });
    await expect(page.getByTestId("chat-answer")).toBeVisible({ timeout: 180_000 });

    const conversationId = new URL(page.url()).searchParams.get("conversationId");
    expect(conversationId).toBeTruthy();

    const beforeMessages = await fetchMessages(page, conversationId!);
    fs.writeFileSync(path.join(EVIDENCE_DIR, "messages-before-edit.json"), JSON.stringify(beforeMessages, null, 2));
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "chat-before-edit.png"), fullPage: true });

    const lastUserBefore = [...beforeMessages].reverse().find((m) => m.role === "USER");
    expect(lastUserBefore).toBeTruthy();
    const userSeqBefore = lastUserBefore!.seq ?? 0;

    const userRow = page
      .getByTestId("chat-message-row")
      .filter({ has: page.getByText(original, { exact: false }) })
      .last();
    await userRow.getByRole("button", { name: /edit|editar/i }).click();

    const editArea = userRow.locator("textarea");
    await expect(editArea).toBeVisible({ timeout: 10_000 });
    await editArea.fill(edited);
    await userRow.getByRole("button", { name: /save.*regenerat|guardar.*regenerar/i }).click();

    await expect(page.getByText(original, { exact: false })).toHaveCount(0, { timeout: 60_000 });
    await expect(page.getByTestId("chat-answer")).toBeVisible({ timeout: 180_000 });

    const afterMessages = await fetchMessages(page, conversationId!);
    fs.writeFileSync(path.join(EVIDENCE_DIR, "messages-after-edit.json"), JSON.stringify(afterMessages, null, 2));
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "chat-after-edit.png"), fullPage: true });

    const userRows = afterMessages.filter((m) => m.role === "USER");
    const editedUsers = userRows.filter((m) => m.content.includes("editada"));
    expect(editedUsers.length, JSON.stringify(userRows, null, 2)).toBe(1);
    expect(editedUsers[0].id).toBe(lastUserBefore!.id);

    const editedUser = editedUsers[0];
    const editedSeq = editedUser.seq ?? userSeqBefore;
    expect(editedSeq).toBe(userSeqBefore);

    const assistantsAfter = afterMessages.filter((m) => m.role === "ASSISTANT" && (m.seq ?? 0) > editedSeq);
    expect(assistantsAfter.length).toBeGreaterThan(0);
    const lastAssistant = assistantsAfter[assistantsAfter.length - 1];
    expect((lastAssistant.seq ?? 0)).toBeGreaterThan(editedSeq);

    for (let i = 1; i < afterMessages.length; i++) {
      const prev = afterMessages[i - 1].seq ?? 0;
      const cur = afterMessages[i].seq ?? 0;
      expect(cur, `messages[${i}] seq must be >= previous`).toBeGreaterThanOrEqual(prev);
    }

    const domRows = await page.getByTestId("chat-message-row").all();
    const domOrder = await Promise.all(
      domRows.map(async (row) => ({
        role: await row.getAttribute("data-message-role"),
        seq: await row.getAttribute("data-message-seq"),
        text: (await row.innerText()).slice(0, 80),
      })),
    );
    fs.writeFileSync(path.join(EVIDENCE_DIR, "dom-message-order.json"), JSON.stringify(domOrder, null, 2));

    const domSeqs = domOrder
      .map((r) => (r.seq != null ? Number(r.seq) : NaN))
      .filter((n) => Number.isFinite(n));
    for (let i = 1; i < domSeqs.length; i++) {
      expect(domSeqs[i]).toBeGreaterThanOrEqual(domSeqs[i - 1]);
    }

    const editedDomIndex = domOrder.findIndex((r) => r.text.includes("editada"));
    const lastAssistantDomIndex = domOrder.map((r) => r.role).lastIndexOf("ASSISTANT");
    expect(editedDomIndex).toBeGreaterThanOrEqual(0);
    expect(lastAssistantDomIndex).toBeGreaterThan(editedDomIndex);
  });
});
