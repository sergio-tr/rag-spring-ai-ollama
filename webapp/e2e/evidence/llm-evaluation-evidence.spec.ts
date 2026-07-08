import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  cancelAllActiveLabJobs,
  collectLlmModelValidation,
  dismissLabJobSessionBannerIfPresent,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  prepareLabE2eTest,
} from "../support/lab-helpers";
import {
  captureEvidence,
  evidenceLog,
  evidenceModelGroup,
  llmModelsForGroup,
  screenshotsOnly,
  shouldRunCampaigns,
} from "../support/evidence-helpers";

test.describe("LLM evaluation evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("LLM evaluation screenshots (no campaign)", async ({ page }) => {
    test.setTimeout(300_000);
    evidenceLog("START llm-evidence screenshots-only");

    await prepareLabE2eTest(page);
    const modelValidation = await collectLlmModelValidation(page);
    evidenceLog(`LLM validation status=${modelValidation.status}`);

    await gotoLabEvaluationPage(page, "llm");
    await dismissLabJobSessionBannerIfPresent(page);
    await assertLabDatasetControlsVisible(page);
    await captureEvidence(page, "llm", "01_llm_page_initial.png", { labPage: true });

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);
    await captureEvidence(page, "llm", "02_llm_dataset_selected.png", { labPage: true });

    const group = evidenceModelGroup();
    const llmModelIds = llmModelsForGroup(group, modelValidation.selectableLlmModelIds);
    const pick = llmModelIds[0] ?? "llama3.2:3b";
    await expect(page.getByTestId("lab-benchmark-llm-models-group")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-benchmark-llm-models-group").scrollIntoViewIfNeeded();
    await captureEvidence(page, "llm", "03_llm_models_panel.png", { labPage: true });
    await page.getByTestId(`lab-benchmark-llm-models-${pick}`).check();
    evidenceLog(`selected LLM group=${group} model=${pick}`);
    await captureEvidence(page, "llm", "04_llm_models_selected.png", { labPage: true });

    const profileBadge = page
      .getByText(/Deterministic|Balanced|Creative|profile/i)
      .or(page.getByTestId("lab-expected-items-summary"));
    await profileBadge.first().scrollIntoViewIfNeeded().catch(() => undefined);
    await captureEvidence(page, "llm", "05_llm_profile_applied.png", { labPage: true });

    const runBtn = page.getByRole("button", { name: /Evaluate selected model|Run evaluation/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await captureEvidence(page, "llm", "06_llm_run_clicked.png", { labPage: true });

    if (shouldRunCampaigns() && !screenshotsOnly()) {
      evidenceLog("SKIP LLM campaign - blocked by Phase 2 scope (screenshot-only for LLM)");
    }
    evidenceLog("PASS llm-evidence screenshots-only");
  });
});
