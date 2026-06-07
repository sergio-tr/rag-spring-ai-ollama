import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { loginAsSeedUser } from "../support/helpers";

test.describe("Closure project create reconciliation @closure @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSeedUser(page);
  });

  test("creates project without false create error when activate is slow @closure", async ({ page }) => {
    const name = uniqueProjectName("closure-create");

    await page.route("**/api/v5/projects", async (route) => {
      if (route.request().method() !== "POST") {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          id: "11111111-1111-1111-1111-111111111101",
          name,
          docCount: 0,
          convCount: 0,
          updatedAt: new Date().toISOString(),
        }),
      });
    });

    await page.route("**/api/v5/projects/*/activate", async (route) => {
      await new Promise((r) => setTimeout(r, 800));
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ activeProjectId: "11111111-1111-1111-1111-111111111101" }),
      });
    });

    const trigger = page.getByRole("button", { name: /new project|nuevo proyecto/i }).first();
    await trigger.click();
    const dialog = page.getByRole("dialog");
    await dialog.locator("#proj-name").fill(name);
    await dialog.getByRole("button", { name: /^(create|crear)$/i }).click();

    await expect(dialog).toBeHidden({ timeout: 30_000 });
    await expect(page.getByTestId("project-create-error")).toHaveCount(0);
    await expect(page.locator('[data-slot="card"]').filter({ hasText: name }).first()).toBeVisible({
      timeout: 20_000,
    });
  });

  test("does not show create error when list refetch fails after successful POST @closure", async ({ page }) => {
    const name = uniqueProjectName("closure-refetch");

    let listCalls = 0;
    await page.route("**/api/v5/projects?*", async (route) => {
      listCalls += 1;
      if (listCalls > 1) {
        await route.fulfill({ status: 503, body: "list unavailable" });
        return;
      }
      await route.continue();
    });

    const trigger = page.getByRole("button", { name: /new project|nuevo proyecto/i }).first();
    await trigger.click();
    const dialog = page.getByRole("dialog");
    await dialog.locator("#proj-name").fill(name);
    await dialog.getByRole("button", { name: /^(create|crear)$/i }).click();

    await expect(dialog).toBeHidden({ timeout: 30_000 });
    await expect(page.getByTestId("project-create-error")).toHaveCount(0);
    await expect(page.locator('[data-slot="card"]').filter({ hasText: name }).first()).toBeVisible({
      timeout: 20_000,
    });
  });
});
