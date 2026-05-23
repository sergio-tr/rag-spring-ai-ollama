import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertLabDatasetControlsVisible,
  clearActiveProjectForLab,
  assertLabRunStarted,
  ensureFirstLlmModelSelectedForRun,
  fetchActiveLabJobs,
  gotoLabEvaluationPage,
  labDatasetRunnable,
} from "../support/lab-helpers";

test.describe("LAB live job and refresh resume @fullstack", () => {
  test("shows live job panel, survives refresh, no stopped-watching copy @fullstack @critical", async ({
    page,
  }) => {
    test.setTimeout(240_000);
    await clearActiveProjectForLab(page);
    await loginAsSeedUser(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID LLM dataset.");

    await ensureFirstLlmModelSelectedForRun(page);

    const runButton = page.getByTestId("lab-llm-run");
    await expect(runButton).toBeEnabled({ timeout: 30_000 });
    await runButton.click();
    await assertLabRunStarted(page);

    const jobPanel = page.getByTestId("lab-job-panel");

    await expect
      .poll(
        async () => {
          const t = (await jobPanel.innerText()) ?? "";
          return /live|en vivo|connecting|conectando|reconnecting|reconectando|running|ejecut/i.test(t);
        },
        { timeout: 45_000, intervals: [500, 1500] },
      )
      .toBe(true);

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
