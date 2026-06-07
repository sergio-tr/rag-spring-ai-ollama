import { expect, test } from "@playwright/test";
import { authHeadersFromPage, productApiUrl } from "./support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  fetchActiveLabJobs,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
  selectLlmModelsForComparison,
} from "./support/lab-helpers";

function resolveStreamUrl(streamPath: string): string {
  if (streamPath.startsWith("http://") || streamPath.startsWith("https://")) {
    return streamPath;
  }
  const suffix = streamPath.replace(/^\/api\/v5/, "");
  return productApiUrl(suffix.startsWith("/") ? suffix : `/${suffix}`);
}

test.describe("LAB model comparison single job @fullstack", () => {
  test("one active job for multi-model campaign @fullstack", async ({ page }) => {
    test.setTimeout(300_000);
    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 25_000, intervals: [250, 750, 1500] })
      .toBe(true);

    const selected = await selectLlmModelsForComparison(page, 3);
    expect(selected, "Need at least 3 LLM models in catalog for comparison campaign").toBe(true);

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

    const headers = await authHeadersFromPage(page);
    const streamUrl = resolveStreamUrl(job!.streamPath!);
    const streamRes = await page.request.get(streamUrl, {
      headers: { ...headers, Accept: "text/event-stream" },
      timeout: 15_000,
    });
    expect(streamRes.status(), await streamRes.text()).toBe(200);
    expect(streamRes.headers()["content-type"] ?? "").toContain("text/event-stream");
    const body = (await streamRes.text()).slice(0, 400);
    expect(body).toMatch(/event:job-event/);
    expect(body).toMatch(/SNAPSHOT|ACCEPTED|CAMPAIGN_/);
  });
});
