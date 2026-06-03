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
    expect(mainText).not.toMatch(/Effective keys:/i);
    expect(mainText).not.toMatch(/adaptiveRoutingEnabled/i);

    const technical = page.getByTestId("chat-config-technical-details");
    await expect(technical).toBeVisible();
    const open = await technical.evaluate((el) => (el as HTMLDetailsElement).open);
    expect(open).toBe(false);

    await technical.locator("summary").click();
    await expect(page.getByTestId("chat-config-effective-keys").or(page.getByText(/Effective keys:/i))).toBeVisible({
      timeout: 8_000,
    });

    await page.getByTestId("chat-config-edit-button").click();
    await expect(page.getByTestId("chat-preset-select")).toBeVisible({ timeout: 8_000 });
  });
});
