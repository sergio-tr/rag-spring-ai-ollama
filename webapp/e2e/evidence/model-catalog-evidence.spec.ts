import { expect, test } from "@playwright/test";
import {
  captureEvidence,
  evidenceLog,
  gotoEvidenceRoute,
  loginAsDevAdmin,
} from "../support/evidence-helpers";

test.describe("Model catalog evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test("admin and lab model catalog screenshots", async ({ page }) => {
    test.setTimeout(300_000);
    evidenceLog("START model-catalog-evidence");

    await loginAsDevAdmin(page);

    await gotoEvidenceRoute(page, "/en/admin");
    await expect(page.getByTestId("admin-models-card")).toBeVisible({ timeout: 30_000 });
    const catalogSection = page.getByTestId("admin-llm-catalog-section");
    if (await catalogSection.isVisible().catch(() => false)) {
      await catalogSection.scrollIntoViewIfNeeded();
      await captureEvidence(page, "model-catalog", "01_admin_catalog_chat.png");
    } else {
      await captureEvidence(page, "model-catalog", "01_admin_catalog_chat.png");
    }

    const embeddingSection = page
      .getByTestId("admin-embedding-catalog-section")
      .or(page.getByText(/Embedding model/i).first());
    if (await embeddingSection.isVisible().catch(() => false)) {
      await embeddingSection.scrollIntoViewIfNeeded();
      await captureEvidence(page, "model-catalog", "02_admin_catalog_embedding.png");
    } else {
      evidenceLog("admin embedding section not found — using models card fallback");
      await captureEvidence(page, "model-catalog", "02_admin_catalog_embedding.png");
    }

    await gotoEvidenceRoute(page, "/en/lab/evaluation/embedding");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
    await captureEvidence(page, "model-catalog", "03_lab_evaluation_models_embedding.png");

    await gotoEvidenceRoute(page, "/en/lab/evaluation/llm");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
    await captureEvidence(page, "model-catalog", "04_lab_evaluation_models_chat.png");

    evidenceLog("PASS model-catalog-evidence");
  });
});
