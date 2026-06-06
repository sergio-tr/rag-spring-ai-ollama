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

test.describe("LAB evaluation navigation recovery @fullstack", () => {
  test("running or completed evaluation survives navigate away and return @fullstack @critical", async ({
    page,
  }) => {
    test.setTimeout(300_000);
    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 25_000, intervals: [250, 750, 1500] })
      .toBe(true);

    await ensureFirstLlmModelSelectedForRun(page);
    await assertLabRunButtonEnabled(page, "lab-llm-run");
    await page.getByTestId("lab-llm-run").click();
    await assertLabRunStarted(page);
    await assertLabJobPanelShowsActivePhase(page, 60_000);

    await page.getByRole("link", { name: /overview|resumen/i }).first().click();
    await expect(page).toHaveURL(/\/lab\/?$/);

    await gotoLabEvaluationPage(page, "llm");

    const activeJobs = await fetchActiveLabJobs(page);
    const panel = page.getByTestId("lab-job-panel");
    const recoveryCta = page.getByTestId("lab-active-job-recovery-cta");
    const resumeButton = page.getByRole("button", { name: /resume watching|reanudar seguimiento/i });

    if (activeJobs.length > 0) {
      await expect(recoveryCta.or(panel).or(resumeButton).first()).toBeVisible({ timeout: 30_000 });
    } else {
      await expect(panel.or(page.getByTestId("lab-benchmark-results-panel"))).toBeVisible({ timeout: 30_000 });
    }

    const forget = page.getByRole("button", { name: /stop watching|dejar de seguir|forget job|olvidar trabajo/i });
    if (await forget.isVisible().catch(() => false)) {
      await forget.click();
      await expect(panel.or(page.getByTestId("lab-benchmark-results-panel"))).toBeVisible({ timeout: 20_000 });
    }
  });
});
