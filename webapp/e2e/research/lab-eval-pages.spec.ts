import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Deep links into lab evaluation sub-routes.
 */
test.describe("Lab evaluation pages", () => {
  test("E2E-13 lab RAG evaluation page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag");
    await expect(page.getByRole("heading", { name: /RAG evaluation/i })).toBeVisible({ timeout: 15_000 });
  });

  test("E2E-14 lab LLM evaluation page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/llm");
    await expect(page.getByRole("heading", { name: /LLM evaluation/i })).toBeVisible({ timeout: 15_000 });
  });
});
