import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

test.describe("Closure chat config compact UX @closure @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSeedUser(page);
  });

  test("compact summary visible; technical keys hidden until expanded @closure", async ({ page }) => {
    await page.goto("/en/chat", { waitUntil: "domcontentloaded", timeout: 20_000 });

    const openConfig = page.getByRole("button", { name: /configuration|configuración/i }).first();
    if (await openConfig.isVisible().catch(() => false)) {
      await openConfig.click();
    } else {
      await page.getByRole("button", { name: /menu|⋮|more/i }).first().click({ timeout: 8_000 }).catch(() => undefined);
      await page.getByRole("menuitem", { name: /configuration|configuración/i }).click({ timeout: 8_000 }).catch(() => undefined);
    }

    const summary = page.getByTestId("chat-config-compact-summary");
    await expect(summary).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("chat-config-summary-model")).toBeVisible();
    await expect(page.getByTestId("chat-config-summary-preset")).toBeVisible();
    await expect(page.getByTestId("chat-config-summary-doc-search")).toBeVisible();
    await expect(page.getByTestId("chat-config-summary-documents")).toBeVisible();
    await expect(page.getByTestId("chat-config-summary-index")).toBeVisible();

    const mainText = (await page.locator("#chat-configuration-side-panel, [data-testid='chat-configuration-side-panel']").innerText().catch(() => "")) || (await page.locator("main").innerText());
    expect(mainText).not.toMatch(/Profile hash/i);
    expect(mainText).not.toMatch(/Active snapshot/i);
    expect(mainText).not.toMatch(/Effective keys:/i);
    expect(mainText).not.toMatch(/adaptiveRoutingEnabled/i);

    const currentSettings = page.getByTestId("chat-config-current-settings");
    await expect(currentSettings).toBeVisible();
    const open = await currentSettings.evaluate((el) => (el as HTMLDetailsElement).open);
    expect(open).toBe(false);

    await currentSettings.locator("summary").click();
    await expect(page.getByTestId("chat-config-effective-keys")).toBeVisible({
      timeout: 8_000,
    });
    const advanced = page.getByTestId("chat-config-advanced-technical");
    await expect(advanced).toBeVisible();
    expect(await advanced.evaluate((el) => (el as HTMLDetailsElement).open)).toBe(false);

    await page.getByTestId("chat-config-edit-button").click();
    await expect(page.getByTestId("chat-preset-select")).toBeVisible({ timeout: 8_000 });
  });
});
