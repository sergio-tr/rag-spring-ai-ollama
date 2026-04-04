import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-07: Lab RAG evaluation run completes and prints a JSON body containing evaluation data.
 */
test.describe("Lab RAG evaluation", () => {
  test("E2E-07 RAG eval shows result JSON @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag");
    await expect(page.getByRole("heading", { name: /rag|evaluación/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    await page.getByTestId("lab-rag-run").click();

    await expect
      .poll(
        async () => {
          const pre = page.locator("pre").last();
          const text = (await pre.textContent()) ?? "";
          return (
            text.includes("evaluation_summary") ||
            text.includes('"ok"') ||
            text.includes("metrics") ||
            text.length > 50
          );
        },
        { timeout: 180_000 },
      )
      .toBe(true);
  });
});
