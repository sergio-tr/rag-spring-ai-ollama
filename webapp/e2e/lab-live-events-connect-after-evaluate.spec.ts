import { expect, test } from "@playwright/test";
import { productApiUrl } from "./support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertLabJobPanelShowsActivePhase,
  assertLabRunButtonEnabled,
  assertNoForbiddenLabCopy,
  assertLabRunStarted,
  ensureFirstLlmModelSelectedForRun,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
} from "./support/lab-helpers";

test.describe("LAB SSE connect after evaluate @fullstack", () => {
  test("does not show Reconnecting before first live connection @fullstack", async ({ page }) => {
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

    await expect
      .poll(() => streamResponses.length > 0, { timeout: 15_000 })
      .toBe(true);
    const stream = streamResponses[streamResponses.length - 1]!;
    expect(stream.url).not.toMatch(/^https?:\/\/localhost:3000\/api\/v5\//);
    expect(stream.url).not.toMatch(/^https?:\/\/127\.0\.0\.1:3000\/api\/v5\//);
    expect(stream.url).toMatch(
      new RegExp(`^${productApiUrl("/lab/jobs/").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}.+/events`),
    );
    expect(stream.status).toBe(200);
    expect(stream.contentType).toContain("text/event-stream");
  });
});
