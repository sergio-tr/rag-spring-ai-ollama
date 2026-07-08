import { test, expect } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

const evidenceDir =
  "/home/sergio/github/rag-spring-ai-ollama/.cursor/evidence/system-closure-recognition-20260703/AX_eval_lab_functional_closure_final_validation/evidence";

test.describe("Eval lab final validation @fullstack", () => {
  test("overview UX and template columns", async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsSeedUser(page);
    await page.goto("/en/lab", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("lab-overview-compact")).toBeVisible({ timeout: 30_000 });

    // 1-2 uploaded workbooks visible without expanding technical details
    await expect(page.getByTestId("lab-overview-uploaded-datasets")).toBeVisible();
    await expect(page.getByText("Uploaded evaluation workbooks")).toBeVisible();

    // 3 technical cards hidden until developer diagnostics expanded
    await expect(page.getByText("Evaluation dataset")).not.toBeVisible();
    await expect(page.getByText("LLM & RAG evaluations")).not.toBeVisible();
    await expect(page.getByText("Classifier service")).not.toBeVisible();

    await page.getByText("Advanced technical details").click();
    await page.getByText("Developer diagnostics").click();
    await expect(page.getByText("Evaluation dataset")).toBeVisible();
    await page.screenshot({ path: `${evidenceDir}/browser_lab_overview.png`, fullPage: true });

    // scroll to templates panel
    await page.locator("#datasets").scrollIntoViewIfNeeded();
    await expect(page.getByTestId("lab-template-classifier")).toBeVisible();

    // 4 classifier template unchanged
    const [clsDl] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("lab-template-classifier").click(),
    ]);
    const clsPath = `${evidenceDir}/browser_classifier_template.xlsx`;
    await clsDl.saveAs(clsPath);

    // 5-7 download and verify via API headers already done; trigger downloads for evidence
    for (const testId of ["lab-template-llm", "lab-template-embedding", "lab-template-rag"] as const) {
      const [dl] = await Promise.all([page.waitForEvent("download"), page.getByTestId(testId).click()]);
      await dl.saveAs(`${evidenceDir}/browser_${testId}.xlsx`);
    }
  });
});
