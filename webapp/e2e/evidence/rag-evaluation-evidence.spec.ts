import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  cancelAllActiveLabJobs,
  dismissLabJobSessionBannerIfPresent,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
  prepareLabRagActaKnowledgeBase,
  selectReferenceRagDataset,
} from "../support/lab-helpers";
import {
  captureEvidence,
  evidenceLog,
  screenshotsOnly,
  shouldRunCampaigns,
} from "../support/evidence-helpers";

test.describe("RAG evaluation evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("RAG evaluation screenshots (no campaign)", async ({ page }) => {
    test.setTimeout(600_000);
    evidenceLog("START rag-evidence screenshots-only");

    await prepareLabE2eTest(page);
    const corpusId = await prepareLabRagActaKnowledgeBase(page);
    evidenceLog(`corpusId=${corpusId}`);

    await gotoLabEvaluationPage(page, "rag");
    await dismissLabJobSessionBannerIfPresent(page);
    await assertLabDatasetControlsVisible(page);
    await captureEvidence(page, "rag", "01_rag_page_initial.png", { labPage: true });

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 90_000, intervals: [500, 1500, 3000] })
      .toBe(true);
    const datasetId = await selectReferenceRagDataset(page);
    evidenceLog(`datasetId=${datasetId}`);
    await captureEvidence(page, "rag", "02_rag_dataset_selected.png", { labPage: true });

    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 15_000 });
    await captureEvidence(page, "rag", "03_rag_corpus_ready.png", { labPage: true });

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await captureEvidence(page, "rag", "04_rag_presets_panel.png", { labPage: true });

    await page.getByTestId("lab-experimental-presets-select-core").click();
    const selected: string[] = [];
    for (let i = 0; i <= 14; i += 1) {
      const code = `P${i}`;
      const box = page.getByTestId(`lab-experimental-preset-${code}`);
      if (await box.isVisible().catch(() => false) && (await box.isChecked())) {
        selected.push(code);
      }
    }
    const demoBest = page.getByTestId("lab-experimental-preset-Demo_Best");
    if (await demoBest.isVisible().catch(() => false) && (await demoBest.isChecked())) {
      selected.push("Demo_Best");
    }
    evidenceLog(`selected presets=${selected.join(",")}`);
    expect(selected.length).toBeGreaterThanOrEqual(2);
    await captureEvidence(page, "rag", "05_rag_presets_selected.png", { labPage: true });

    const resetRecommended = page.getByRole("button", { name: /Reset to recommended/i });
    if (await resetRecommended.isVisible().catch(() => false)) {
      await resetRecommended.click().catch(() => undefined);
    }

    const runBtn = page.getByTestId("lab-rag-run");
    const runEnabled = await runBtn.isEnabled().catch(() => false);
    evidenceLog(`RAG run button enabled=${runEnabled}`);
    await captureEvidence(page, "rag", "06_rag_run_clicked.png", { labPage: true });

    if (shouldRunCampaigns() && !screenshotsOnly()) {
      evidenceLog("SKIP RAG campaign - blocked by Phase 2 scope (screenshot-only for RAG)");
    }
    evidenceLog("PASS rag-evidence screenshots-only");
  });
});
