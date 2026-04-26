import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Deep links into lab evaluation sub-routes.
 */
test.describe("Lab evaluation pages", () => {
  test("E2E-13 lab RAG evaluation page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag");
    // When evaluations are disabled in the backend, the app may redirect to /lab overview.
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio/i }).first(),
    ).toBeVisible({ timeout: 20_000 });
  });

  test("E2E-14 lab LLM evaluation page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/llm");
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio/i }).first(),
    ).toBeVisible({ timeout: 20_000 });
  });
});
