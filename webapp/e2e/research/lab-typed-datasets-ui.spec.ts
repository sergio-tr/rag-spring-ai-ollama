import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Full-stack Lab flows for typed experimental datasets (needs Spring + Next + seed user).
 * Tagged {@link @fullstack} — excluded from default `npm run test:e2e`.
 */
test.describe("Lab typed datasets UI @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: /research lab/i })).toBeVisible({
      timeout: 20_000,
    });
  });

  test("overview shows bundle/status JSON without legacy workbook filename", async ({ page }) => {
    await page.getByText("Raw JSON", { exact: true }).click();
    const raw = page.locator("pre").filter({ hasText: /referenceBundleAvailable/ });
    await expect(raw).toBeVisible({ timeout: 15_000 });
    await expect(raw).not.toContainText("evaluation_dataset.xlsx");
    await expect(raw).toContainText("countsByDatasetKind");
  });

  test("download LLM, embedding, RAG preset templates (.xlsx)", async ({ page }) => {
    const downloadDir = path.join(process.cwd(), "test-results", "lab-templates-ui");
    fs.mkdirSync(downloadDir, { recursive: true });

    const templates = [
      { testId: "lab-template-llm", key: "llm-model-baseline" },
      { testId: "lab-template-embedding", key: "embedding-baseline" },
      { testId: "lab-template-rag", key: "rag-preset-benchmark" },
    ] as const;

    for (const { testId, key } of templates) {
      const [download] = await Promise.all([
        page.waitForEvent("download"),
        page.getByTestId(testId).click(),
      ]);
      expect(download.suggestedFilename()).toMatch(/\.xlsx$/i);
      const target = path.join(downloadDir, `${key}-${download.suggestedFilename()}`);
      await download.saveAs(target);
      const magic = fs.readFileSync(target).subarray(0, 2).toString("latin1");
      expect(magic).toBe("PK");
    }
  });

  test("upload valid LLM template shows validation accepted", async ({ page }) => {
    const downloadDir = path.join(process.cwd(), "test-results", "lab-templates-ui");
    fs.mkdirSync(downloadDir, { recursive: true });
    await page.locator("#lab-exp-dataset-kind").selectOption("llm-model-baseline");
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("lab-template-llm").click(),
    ]);
    const saved = path.join(downloadDir, `upload-valid-${download.suggestedFilename()}`);
    await download.saveAs(saved);

    await page.locator("#lab-exp-dataset-file").setInputFiles(saved);
    await page.getByRole("button", { name: /^upload & validate$/i }).click();
    await expect(page.getByText(/validation \(accepted\)/i)).toBeVisible({ timeout: 30_000 });
  });

  test("upload LLM bytes under embedding kind shows validation rejected + issue codes", async ({
    page,
  }) => {
    const downloadDir = path.join(process.cwd(), "test-results", "lab-templates-ui");
    fs.mkdirSync(downloadDir, { recursive: true });
    await page.locator("#lab-exp-dataset-kind").selectOption("llm-model-baseline");
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("lab-template-llm").click(),
    ]);
    const saved = path.join(downloadDir, `upload-invalid-${download.suggestedFilename()}`);
    await download.saveAs(saved);

    await page.locator("#lab-exp-dataset-kind").selectOption("embedding-baseline");
    await page.locator("#lab-exp-dataset-file").setInputFiles(saved);
    await page.getByRole("button", { name: /^upload & validate$/i }).click();
    await expect(page.getByText(/validation \(rejected\)/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Has errors|Con errores/i)).toBeVisible();
    await expect(page.locator("li").filter({ has: page.locator(".font-mono") }).first()).toBeVisible();
  });

  test("LLM benchmark card can start canonical run (best-effort)", async ({ page }) => {
    await page.goto("/en/lab/evaluation/llm", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: /research lab/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    const runBtn = page.getByTestId("lab-llm-run");
    await expect(runBtn).toBeVisible({ timeout: 10_000 });
    test.skip((await runBtn.isDisabled()) === true, "Run disabled — no compatible typed dataset");
    await runBtn.click();
    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 60_000 });
  });
});
