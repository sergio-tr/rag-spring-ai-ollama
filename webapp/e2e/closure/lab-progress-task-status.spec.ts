import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabJobPanelShowsActivePhase,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  ensureLlmCampaignModelsReady,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  prepareLabE2eTest,
  selectLlmModelsByIds,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";
import { authHeadersFromPage, productApiUrl } from "../support/helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.docs/evidence/p0-lab-rag-runtime-closure/progress",
);

function evidenceLog(line: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.appendFileSync(
    path.join(EVIDENCE_DIR, "e2e-lab-progress-task-status.log"),
    `${new Date().toISOString()} ${line}\n`,
    "utf8",
  );
}

test.describe("Closure LAB progress and task status @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("LLM comparison shows campaign item counter and live subtasks", async ({ page }) => {
    test.setTimeout(900_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(path.join(EVIDENCE_DIR, "e2e-lab-progress-task-status.log"), "", "utf8");
    evidenceLog("START lab-progress-task-status");

    await prepareLabE2eTest(page);

    const llmModelIds = await ensureLlmCampaignModelsReady(page, 3);
    evidenceLog(`models=${llmModelIds.join(",")}`);
    expect(llmModelIds.length).toBeGreaterThanOrEqual(3);

    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    await selectLlmModelsByIds(page, llmModelIds.slice(0, 3));
    await expect(page.getByTestId("lab-comparison-selection-hint")).toContainText(/3/i);
    await expect(page.getByTestId("lab-expected-items-summary")).toContainText(/108/i);

    trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    const job = await waitForSingleActiveLabJob(page, "LLM_JUDGE_QA");
    evidenceLog(`jobId=${job.jobId}`);
    expect(job.jobId).toBeTruthy();

    await assertLabJobPanelShowsActivePhase(page, 90_000);
    await assertNoForbiddenLabCopy(page);

    const technical = page.getByTestId("lab-technical-events");
    await expect(technical).toBeVisible();
    await expect(technical).not.toHaveAttribute("open");

    await expect
      .poll(
        async () => {
          const counter = page.getByTestId("lab-job-item-counter");
          if (!(await counter.isVisible().catch(() => false))) return "";
          return (await counter.textContent()) ?? "";
        },
        { timeout: 300_000, intervals: [2000, 5000] },
      )
      .toMatch(/\/\s*108/);

    await expect
      .poll(
        async () => {
          const modelLine = page.getByTestId("lab-job-active-model");
          return (await modelLine.isVisible().catch(() => false)) ? await modelLine.textContent() : "";
        },
        { timeout: 120_000, intervals: [2000, 5000] },
      )
      .not.toBe("");

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "progress-running.png"), fullPage: true });

    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    evidenceLog(`terminal status=${terminal.status}`);
    expect((terminal.status ?? "").toUpperCase()).toBe("SUCCEEDED");

    const headers = await authHeadersFromPage(page);
    const jobJson = await (
      await page.request.get(productApiUrl(`/lab/jobs/${job.jobId}`), { headers })
    ).json();
    fs.writeFileSync(
      path.join(EVIDENCE_DIR, "job-snapshot-final.json"),
      `${JSON.stringify(jobJson, null, 2)}\n`,
      "utf8",
    );

    const eventsRes = await page.request.get(
      productApiUrl(`/lab/jobs/${job.jobId}/events?stream=false`),
      { headers },
    );
    if (eventsRes.ok()) {
      const eventsBody = await eventsRes.json();
      fs.writeFileSync(
        path.join(EVIDENCE_DIR, "event-stream-sample.json"),
        `${JSON.stringify(eventsBody, null, 2)}\n`,
        "utf8",
      );
    }

    await expect(page.getByTestId("lab-subtask-list")).toBeVisible();
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "progress-completed.png"), fullPage: true });
  });
});
