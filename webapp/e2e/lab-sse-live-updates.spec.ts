import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabJobPanelShowsActivePhase,
  assertLabRunButtonEnabled,
  assertNoForbiddenLabCopy,
  ensureFirstLlmModelSelectedForRun,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
} from "./support/lab-helpers";

test.describe("LAB SSE live updates @fullstack", () => {
  test("exits connecting within 10s after evaluate @fullstack", async ({ page }) => {
    test.setTimeout(240_000);
    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 25_000, intervals: [250, 750, 1500] })
      .toBe(true);

    await ensureFirstLlmModelSelectedForRun(page);
    await assertLabRunButtonEnabled(page, "lab-llm-run");
    const runButton = page.getByTestId("lab-llm-run");

    const jobPanel = page.getByTestId("lab-job-panel");
    await runButton.click();
    await expect(jobPanel).toBeVisible({ timeout: 30_000 });

    await expect
      .poll(async () => {
        const phase = await jobPanel.getAttribute("data-lab-job-ui-phase");
        return phase !== "connecting";
      }, { timeout: 10_000 })
      .toBe(true);

    await assertLabJobPanelShowsActivePhase(page, 90_000);
    await assertNoForbiddenLabCopy(page);
  });
});
