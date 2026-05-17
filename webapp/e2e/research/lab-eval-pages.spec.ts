import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Deep links into lab benchmark runner sub-routes (LLM / embedding / RAG preset).
 */
test.describe("Lab evaluation pages", () => {
  test("E2E-13 lab RAG benchmark page loads @fullstack @critical", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag", { waitUntil: "domcontentloaded", timeout: 15_000 });
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio/i }).first(),
    ).toBeVisible({ timeout: 20_000 });
  });

  test("E2E-14 lab LLM evaluation page loads @fullstack @critical", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/llm", { waitUntil: "domcontentloaded", timeout: 15_000 });
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio/i }).first(),
    ).toBeVisible({ timeout: 20_000 });
  });

  test("E2E-15 lab embedding evaluation page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/embedding", { waitUntil: "domcontentloaded", timeout: 15_000 });
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio/i }).first(),
    ).toBeVisible({ timeout: 20_000 });
  });
});
