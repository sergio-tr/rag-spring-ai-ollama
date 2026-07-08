import { test, expect } from "@playwright/test";
import * as fs from "node:fs";
import { loginAsSeedUser } from "../support/helpers";

const evidenceDir =
  "/home/sergio/github/rag-spring-ai-ollama/.cursor/evidence/system-closure-recognition-20260703/AX_eval_lab_functional_closure_final_validation/evidence";

test.describe("Eval lab upload validation @fullstack", () => {
  test("invalid old LLM template shows expected_answer validation error in UI", async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsSeedUser(page);
    await page.goto("/en/lab#datasets", { waitUntil: "domcontentloaded" });
    const file = `${evidenceDir}/upload_old_llm_ui.xlsx`;
    if (!fs.existsSync(file)) {
      test.skip(true, "run python template builder first");
    }
    await page.locator("#lab-exp-dataset-kind").selectOption("llm-model-baseline");
    await page.locator("#lab-exp-dataset-file").setInputFiles(file);
    await page.getByRole("button", { name: /^upload & validate$/i }).click();
    await expect(page.getByText(/validation \(rejected\)/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.locator("li").filter({ hasText: /expected_answer/i }).first()).toBeVisible();
    await page.screenshot({ path: `${evidenceDir}/browser_upload_invalid_llm.png`, fullPage: true });
  });
});
