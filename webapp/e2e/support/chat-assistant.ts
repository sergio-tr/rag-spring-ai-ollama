import { expect, type Page } from "@playwright/test";

/**
 * Waits for the latest assistant-style bubble (left-aligned chat column) to have non-trivial text.
 * Matches the project chat DOM (`mr-auto max-w-[85%]` assistant rows).
 */
export async function waitForLatestAssistantNonEmpty(page: Page, timeoutMs = 120_000): Promise<string> {
  const row = page.locator("div.mr-auto.max-w-\\[85\\%\\]").last();
  await expect(row).toBeVisible({ timeout: timeoutMs });
  await expect
    .poll(
      async () => (await row.innerText()).trim().length,
      { timeout: timeoutMs, intervals: [300, 600, 1200] },
    )
    .toBeGreaterThan(2);
  return (await row.innerText()).trim();
}

/** User-visible alerts must not contain HTML error pages or gateway boilerplate. */
export async function assertChatAlertsAreSanitized(page: Page): Promise<void> {
  const alerts = page.getByRole("alert");
  const count = await alerts.count();
  for (let i = 0; i < count; i++) {
    const t = (await alerts.nth(i).textContent()) ?? "";
    expect(t).not.toMatch(/<\s*html/i);
    expect(t).not.toMatch(/<!doctype\s+html/i);
    expect(t).not.toMatch(/502\s+bad\s+gateway/i);
  }
}
