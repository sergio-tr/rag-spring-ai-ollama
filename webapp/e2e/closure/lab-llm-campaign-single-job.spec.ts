import { expect, test } from "@playwright/test";
import { productApiUrl } from "../support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  fetchLabJobStatus,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectLlmModelsForComparison,
  trackBenchmarkCampaignAccepted,
  trackLabJobSseResponses,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

test.describe("Closure LAB LLM campaign @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("LLM campaign: single job campaign with one jobId and comparison @closure @fullstack", async ({
    page,
  }) => {
    test.setTimeout(600_000);
    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID LLM dataset.");

    const selected = await selectLlmModelsForComparison(page, 2);
    test.skip(!selected, "Fewer than two LLM models in catalog.");

    const sse = trackLabJobSseResponses(page);
    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();
    await assertLabRunStarted(page);
    await expect
      .poll(() => Boolean(acceptedCapture.accepted.campaignId), { timeout: 15_000 })
      .toBe(true);

    const job = await waitForSingleActiveLabJob(page, "LLM_JUDGE_QA");
    expect(job.jobId).toBeTruthy();
    expect(job.streamPath).toMatch(/\/api\/v5\/lab\/jobs\/.+\/events/);

    await expect.poll(() => sse.responses.length > 0, { timeout: 20_000 }).toBe(true);
    const stream = sse.responses[sse.responses.length - 1]!;
    expect(stream.status).toBe(200);
    expect(stream.contentType).toContain("text/event-stream");
    expect(stream.url).toMatch(
      new RegExp(`^${productApiUrl("/lab/jobs/").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}.+/events`),
    );

    const terminal = await pollLabJobTerminal(page, job.jobId!, 540_000);
    const terminalStatus = (terminal.status ?? "").toUpperCase();
    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    const totalItems = acceptedCapture.accepted.totalItems ?? terminal.totalItems;
    expect(campaignId).toBeTruthy();
    if (totalItems != null && totalItems > 0) {
      expect(totalItems).toBeGreaterThan(0);
    }

    expect(terminalStatus, "LLM campaign must finish successfully").toBe("SUCCEEDED");

    const outcome = await pollLabTerminalOutcome(page, 60_000);
    expect(["comparison", "results", "job_done"]).toContain(outcome);
    await expect(page.getByTestId("lab-campaign-comparison-panel")).toBeVisible({ timeout: 30_000 });
    await assertNoForbiddenLabCopy(page);

    const statusAfter = await fetchLabJobStatus(page, job.jobId!);
    expect(statusAfter.status?.toUpperCase()).toBe("SUCCEEDED");
  });
});
