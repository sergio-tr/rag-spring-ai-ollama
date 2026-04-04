import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * E2E-05: ingest a doc, then one chat turn — SSE {@code done} includes retrieval sources + pipeline panel.
 */
test.describe("Chat RAG", () => {
  test("E2E-05 send message completes with stub answer pipeline and sources @fullstack", async ({
    page,
  }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-chat"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-sources.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("SOURCE_MARKER_E2E_05 retrieval smoke content.\n"),
    });
    await expect.poll(
      async () => {
        const row = page.locator("tbody tr").filter({ hasText: "e2e-sources.txt" });
        if ((await row.count()) === 0) return false;
        return (await row.getByText("READY", { exact: true }).count()) > 0;
      },
      { timeout: 120_000 },
    ).toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByRole("button", { name: /new conversation|nueva conversación/i }).click();
    const textarea = page.getByPlaceholder(/message|mensaje/i);
    await expect(textarea).toBeEnabled({ timeout: 15_000 });
    await textarea.fill("What is in my project documents?");
    await page.getByRole("button", { name: /^send$|^enviar$/i }).click();

    await expect.poll(
      async () => page.getByText(/E2E stub reply/i).isVisible(),
      { timeout: 120_000, intervals: [500, 1_000, 2_000] },
    ).toBe(true);

    await expect(page.getByRole("heading", { name: /^pipeline$/i })).toBeVisible();
    await expect(page.getByText(/classification/i).first()).toBeVisible();

    await expect(page.getByRole("heading", { name: /sources|fuentes/i })).toBeVisible();
    await expect(page.getByText(/SOURCE_MARKER_E2E_05|e2e-sources\.txt/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });
});
