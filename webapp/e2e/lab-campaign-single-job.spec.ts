import { expect, test } from "@playwright/test";
import { productApiUrl } from "./support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  fetchActiveLabJobs,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
  selectLlmModelsForComparison,
  trackLabJobSseResponses,
} from "./support/lab-helpers";

test.describe("LAB campaign single job @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test("single job campaign: one jobId and one stream for LLM campaign @fullstack", async ({ page }) => {
    test.setTimeout(300_000);
    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 25_000, intervals: [250, 750, 1500] })
      .toBe(true);

    const selected = await selectLlmModelsForComparison(page, 3);
    expect(selected, "Need at least 3 LLM models in catalog for comparison campaign").toBe(true);

    const sse = trackLabJobSseResponses(page);
    const runButton = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runButton).toBeEnabled({ timeout: 20_000 });
    await runButton.click();
    await assertLabRunStarted(page);

    let jobId: string | undefined;
    await expect
      .poll(async () => {
        const active = await fetchActiveLabJobs(page);
        const llm = active.filter((j) => j.benchmarkKind === "LLM_JUDGE_QA");
        if (llm.length === 1) {
          jobId = llm[0]?.jobId;
          return true;
        }
        return false;
      }, { timeout: 90_000 })
      .toBe(true);

    const active = await fetchActiveLabJobs(page);
    const job = active.find((j) => j.jobId === jobId);
    expect(job?.streamPath).toMatch(/\/api\/v5\/lab\/jobs\/.+\/events/);

    await expect.poll(() => sse.responses.length > 0, { timeout: 20_000 }).toBe(true);
    const stream = sse.responses[sse.responses.length - 1]!;
    expect(stream.status).toBe(200);
    expect(stream.contentType).toContain("text/event-stream");
    expect(stream.url).toMatch(
      new RegExp(`^${productApiUrl("/lab/jobs/").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}.+/events`),
    );

    const jobPanel = page.getByTestId("lab-job-panel");
    await expect(jobPanel.getByText(/^Reconnecting/i)).toHaveCount(0, { timeout: 8_000 });
  });
});
