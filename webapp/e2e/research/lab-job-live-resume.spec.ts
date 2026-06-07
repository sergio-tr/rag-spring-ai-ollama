import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabJobPanelShowsActivePhase,
  assertLabRunButtonEnabled,
  assertLabRunStarted,
  ensureFirstLlmModelSelectedForRun,
  fetchActiveLabJobs,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
} from "../support/lab-helpers";

test.describe("LAB live job and refresh resume @fullstack", () => {
  test("shows live job panel, survives refresh, no stopped-watching copy @fullstack @critical", async ({
    page,
  }) => {
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
    await runButton.click();
    await assertLabRunStarted(page);

    await assertLabJobPanelShowsActivePhase(page, 60_000);

    await expect(page.getByText(/Stopped watching here/i)).toHaveCount(0);
    await expect(page.getByText(/Stopped waiting — the server job/i)).toHaveCount(0);

    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    const activeJobs = await fetchActiveLabJobs(page);
    const recoveryCta = page.getByTestId("lab-active-job-recovery-cta");
    const banner = page.getByTestId("lab-job-session-banner");
    const resumedPanel = page.getByTestId("lab-job-panel");

    if (activeJobs.length > 0) {
      await expect(recoveryCta.or(banner).or(resumedPanel).first()).toBeVisible({ timeout: 20_000 });
    }

    await expect(page.getByText(/Stopped watching here/i)).toHaveCount(0);
  });
});
