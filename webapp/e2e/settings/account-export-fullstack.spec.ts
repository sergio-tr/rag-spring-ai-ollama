import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Phase 8C — account export async job surfaces queued/running/terminal or controlled API error (no infinite spinner).
 */
test.describe("Settings account export @fullstack", () => {
  test("request export reaches terminal, download, or controlled alert @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.evaluate(() => {
      try {
        sessionStorage.removeItem("rag-account-export-session-v1");
      } catch {
        /* ignore */
      }
    });

    await page.goto("/en/settings/account");
    await expect(page.getByTestId("settings-account-export")).toBeVisible({ timeout: 20_000 });

    const requestBtn = page.getByTestId("account-export-request");
    await expect(requestBtn).toBeVisible();
    await requestBtn.click();

    await expect(page.getByTestId("account-export-status")).toBeVisible({ timeout: 60_000 });

    await expect
      .poll(async () => {
        const working =
          (await page.getByTestId("account-export-request").textContent())?.includes("Working on export") ??
          false;
        const disabled = await page.getByTestId("account-export-request").isDisabled();
        return !(working && disabled);
      }, { timeout: 180_000 })
      .toBe(true);

    await expect(page.getByTestId("account-export-request")).toBeEnabled({ timeout: 10_000 });
  });
});
