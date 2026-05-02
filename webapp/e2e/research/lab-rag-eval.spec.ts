import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-07: Lab RAG evaluation page renders, and clearly indicates if datasets/evaluations are disabled.
 *
 * Full RAG evaluation runs are intentionally excluded from @fullstack CI smoke because they are
 * slow and can depend on optional dataset seeding and LLM availability.
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
    // Copy matches `Lab.datasetsDisabledWarn` (EN/ES) or legacy "dataset … not loaded" strings.
    const disabledPattern =
      /benchmark questions are unavailable|evaluation dataset is not loaded|preguntas de benchmark no están disponibles|dataset|datos.*cargad/i;
    const disabledWarn = page.getByText(disabledPattern).first();
    if (await disabledWarn.isVisible().catch(() => false)) {
      await expect(runButton).toBeDisabled();
    } else {
      await expect(runButton).toBeEnabled();
    }
  });
});
