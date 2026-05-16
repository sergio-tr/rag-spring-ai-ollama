import { expect, type Page, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

type DemoBenchmark = {
  path: string;
  runTestId: string;
  name: string;
  configure?: (page: Page) => Promise<void>;
};

async function assertRunnableBenchmarkPage(page: Page, benchmark: DemoBenchmark): Promise<void> {
  await page.goto(benchmark.path);
  await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
    timeout: 20_000,
  });

  const datasetSelect = page.getByTestId("lab-benchmark-dataset-select");
  await expect(datasetSelect).toBeVisible({ timeout: 30_000 });
  const needsDataset = page.getByTestId("lab-benchmark-needs-dataset-warn");
  await expect(
    needsDataset,
    `${benchmark.name} requires a compatible dataset in the prepared demo environment.`,
  ).toHaveCount(0);
  await expect(datasetSelect).not.toHaveValue("");

  await benchmark.configure?.(page);

  const runButton = page.getByTestId(benchmark.runTestId);
  await expect(runButton, `${benchmark.name} run button must be enabled in demo mode`).toBeEnabled({
    timeout: 45_000,
  });
  await runButton.click();

  const jobPanel = page.getByTestId("lab-job-panel");
  const resultsPanel = page.getByTestId("lab-benchmark-results-panel");
  const rawSummary = page.locator("summary").filter({ hasText: /Raw async payload|JSON.*advanced/i });
  const errorAlert = page.locator('[data-slot="card"]').getByRole("alert").first();

  await expect
    .poll(
      async () => {
        const result = await resultsPanel.isVisible().catch(() => false);
        const raw = await rawSummary.isVisible().catch(() => false);
        const job = await jobPanel.isVisible().catch(() => false);
        const err = await errorAlert.isVisible().catch(() => false);
        return result || raw || job || err;
      },
      { timeout: 180_000, intervals: [500, 1500, 3000] },
    )
    .toBe(true);

  const hasError = await errorAlert.isVisible().catch(() => false);
  expect(hasError, `${benchmark.name} should not finish as a visible Lab error`).toBe(false);

  await expect
    .poll(
      async () => {
        if (await resultsPanel.isVisible().catch(() => false)) return "results";
        if (await rawSummary.isVisible().catch(() => false)) return "raw";
        const jobText = (await jobPanel.textContent().catch(() => "")) ?? "";
        if (/FAILED|CANCELLED/i.test(jobText)) return "failed";
        if (/SUCCEEDED/i.test(jobText)) return "succeeded";
        return "running";
      },
      { timeout: 180_000, intervals: [1000, 2500, 5000] },
    )
    .toMatch(/^(results|raw|succeeded)$/);
}

test.describe.serial("Demo Lab evaluation flows @fullstack @demoHeavy", () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(300_000);
    await loginAsSeedUser(page);
  });

  test("LAB LLM evaluation starts and reaches terminal output @fullstack", async ({ page }) => {
    await assertRunnableBenchmarkPage(page, {
      path: "/en/lab/evaluation/llm",
      runTestId: "lab-llm-run",
      name: "LAB LLM evaluation",
    });
  });

  test("LAB embedding evaluation starts and reaches terminal output @fullstack", async ({ page }) => {
    await assertRunnableBenchmarkPage(page, {
      path: "/en/lab/evaluation/embedding",
      runTestId: "lab-embedding-run",
      name: "LAB embedding evaluation",
    });
  });

  test("LAB RAG P0-P14 evaluation can run a reduced core subset @fullstack", async ({ page }) => {
    await assertRunnableBenchmarkPage(page, {
      path: "/en/lab/evaluation/rag",
      runTestId: "lab-rag-run",
      name: "LAB RAG P0-P14 evaluation",
      configure: async (p) => {
        await expect(p.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
        await p.getByTestId("lab-experimental-presets-select-core").click();
        await expect(p.getByTestId("lab-expected-items-summary")).toBeVisible({ timeout: 15_000 });
      },
    });
  });
});
