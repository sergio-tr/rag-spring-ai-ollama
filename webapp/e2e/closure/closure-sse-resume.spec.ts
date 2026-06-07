import { expect, test } from "@playwright/test";
import { productApiUrl } from "../support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertLabJobPanelShowsActivePhase,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  assertLabRunButtonEnabled,
  ensureFirstLlmModelSelectedForRun,
  fetchActiveLabJobs,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  cancelAllActiveLabJobs,
  prepareLabE2eTest,
} from "../support/lab-helpers";

/**
 * Closure: SSE must not flash Reconnecting before first live connection; refresh resumes same job.
 * Serial: shares one backend Lab job namespace; parallel workers cause "Another Lab evaluation is already running".
 */
test.describe.configure({ mode: "serial" });

test.describe("Closure LAB SSE and resume @closure @fullstack", () => {
  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page).catch(() => undefined);
  });

  test("no Reconnecting before connecting/live; events stream @closure @fullstack", async ({ page }) => {
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
    const streamResponses: { url: string; status: number; contentType: string }[] = [];
    page.on("response", (res) => {
      const url = res.url();
      if (!url.includes("/lab/jobs/") || !url.includes("/events")) return;
      streamResponses.push({
        url,
        status: res.status(),
        contentType: res.headers()["content-type"] ?? "",
      });
    });

    await runButton.click();
    await assertLabRunStarted(page);
    await expect(jobPanel).toBeVisible({ timeout: 30_000 });
    await expect(jobPanel.getByText(/^Reconnecting/i)).toHaveCount(0, { timeout: 8_000 });

    await expect
      .poll(async () => {
        const phase = await jobPanel.getAttribute("data-lab-job-ui-phase");
        return phase === "connecting" || phase === "live" || phase === "running" || phase === "queued";
      }, { timeout: 45_000 })
      .toBe(true);

    await assertLabJobPanelShowsActivePhase(page, 90_000);
    await assertNoForbiddenLabCopy(page);

    await expect.poll(() => streamResponses.length > 0, { timeout: 15_000 }).toBe(true);
    const stream = streamResponses[streamResponses.length - 1]!;
    expect(stream.url).toMatch(
      new RegExp(`^${productApiUrl("/lab/jobs/").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}.+/events`),
    );
    expect(stream.status).toBe(200);
    expect(stream.contentType).toContain("text/event-stream");
    await expect(page.getByText(/Live stream configuration error/i)).toHaveCount(0);
  });

  test("stop after evaluate re-enables Run without configuration error @closure @fullstack", async ({
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
    const stopButton = page.getByTestId("lab-eval-stop");

    await runButton.click();
    await assertLabRunStarted(page);
    await assertLabJobPanelShowsActivePhase(page, 90_000);
    await expect(page.getByText(/Live stream configuration error/i)).toHaveCount(0);

    await expect(stopButton).toBeEnabled({ timeout: 30_000 });
    await stopButton.click();
    const confirm = page.getByTestId("lab-job-stop-confirm-dialog");
    await expect(confirm).toBeVisible({ timeout: 10_000 });
    await confirm.getByTestId("lab-job-stop-confirm-button").click();

    await expect
      .poll(
        async () => {
          const disabled = await runButton.isDisabled();
          return !disabled;
        },
        { timeout: 90_000, intervals: [500, 1000, 2000] },
      )
      .toBe(true);
    await assertLabRunButtonEnabled(page, "lab-llm-run");
    await expect(page.getByText(/Live stream configuration error/i)).toHaveCount(0);
  });

  test("refresh keeps active job recoverable @closure @fullstack", async ({ page }) => {
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

    const jobsBefore = await fetchActiveLabJobs(page);
    const jobIdBefore = jobsBefore[0]?.jobId;
    expect(jobIdBefore, "Run must create an active Lab job before refresh resume check").toBeTruthy();

    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    const jobsAfter = await fetchActiveLabJobs(page);
    const sameJob = jobsAfter.find((j) => j.jobId === jobIdBefore);
    expect(sameJob?.jobId).toBe(jobIdBefore);

    const recoveryCta = page.getByTestId("lab-active-job-recovery-cta");
    const banner = page.getByTestId("lab-job-session-banner");
    const panel = page.getByTestId("lab-job-panel");
    await expect(recoveryCta.or(banner).or(panel).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Stopped watching here/i)).toHaveCount(0);
  });
});
