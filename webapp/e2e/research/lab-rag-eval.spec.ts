import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-07: Lab RAG evaluation — UI matches {@link lab-evaluation-run-card} contract:
 * {@code datasetsReady} drives both the amber warning and Run disabled state; do not infer from unrelated "dataset" copy.
 *
 * When datasets are enabled (typical fullstack seed), runs a synchronous evaluation and expects JSON output.
 */
test.describe("Lab RAG evaluation", () => {
  test("E2E-07 RAG eval shows result JSON @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag");
    await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    const runButton = page.getByTestId("lab-rag-run");
    await expect(runButton).toBeVisible({ timeout: 20_000 });

    const warn = page.getByTestId("lab-datasets-disabled-warn");

    await expect
      .poll(
        async () => {
          const warnVisible = await warn.isVisible().catch(() => false);
          const disabled = await runButton.isDisabled();
          return warnVisible === disabled;
        },
        { timeout: 30_000, intervals: [200, 400, 600] },
      )
      .toBe(true);

    if (await runButton.isDisabled()) {
      await expect(warn).toBeVisible();
      return;
    }

    await expect(warn).toHaveCount(0);

    await page
      .getByRole("checkbox", {
        name: /Return the full result in this tab|Devolver el resultado completo/i,
      })
      .check();
    await runButton.click();

    const resultPre = page.locator('[data-slot="card"] pre').first();
    await expect(resultPre).toBeVisible({ timeout: 180_000 });
    await expect
      .poll(async () => {
        const t = (await resultPre.textContent())?.trim() ?? "";
        return t.length > 2 && (t.startsWith("{") || t.startsWith("["));
      }, { timeout: 5000 })
      .toBe(true);
  });
});
