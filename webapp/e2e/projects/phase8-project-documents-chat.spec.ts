import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser, sendChatMessage } from "../support/helpers";

/**
 * Phase 8C — end-to-end journey: seeded login → active project → documents → READY ingest → chat send.
 * Complements narrower specs (E2E-03, E2E-S5-02, E2E-05) with one chained navigation narrative.
 */
test.describe("Phase 8 project documents chat journey @fullstack", () => {
  test("login, documents READY, chat shows user message without send strip error @fullstack", async ({
    page,
  }) => {
    await loginAsSeedUser(page);
    const name = uniqueProjectName("e2e-p8-journey");
    await createAndActivateProject(page, name);

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /^documents$/i })).toBeVisible({ timeout: 15_000 });

    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-p8-journey.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("phase8 journey marker for retrieval.\n"),
    });
    await expect
      .poll(
        async () => {
          const row = page.locator("tbody tr").filter({ hasText: "e2e-p8-journey.txt" });
          if ((await row.count()) === 0) return false;
          return (await row.getByText("READY", { exact: true }).count()) > 0;
        },
        { timeout: 120_000 },
      )
      .toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);
    await page.getByTestId("chat-new-conversation").click();

    await sendChatMessage(page, "Phase 8 journey ping");
    await expect(page.getByText("Phase 8 journey ping")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/could not send message|no se pudo enviar/i)).toHaveCount(0);
  });
});
