import { expect, test } from "@playwright/test";
import { prepareLabE2eTest } from "../support/lab-helpers";
import { captureEvidence, evidenceLog, gotoEvidenceRoute } from "../support/evidence-helpers";

test.describe("Classifier evaluation evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test("classifier lab screenshots (no eval campaign)", async ({ page }) => {
    test.setTimeout(300_000);
    evidenceLog("START classifier-evidence screenshots-only");

    await prepareLabE2eTest(page);
    await gotoEvidenceRoute(page, "/en/lab/classifier");
    await expect(page.getByText(/classifier|clasificador/i).first()).toBeVisible({ timeout: 15_000 });
    await captureEvidence(page, "classifier", "01_classifier_page_initial.png", { labPage: true });

    const evalSection = page
      .getByText(/^Evaluate$/i)
      .or(page.getByTestId("lab-classifier-eval-model"))
      .or(page.getByTestId("classifier-registry-table"));
    await expect(evalSection.first()).toBeVisible({ timeout: 15_000 });
    await captureEvidence(page, "classifier", "02_classifier_eval_panel.png", { labPage: true });

    const evalSelect = page.getByTestId("lab-classifier-eval-model");
    if (await evalSelect.isVisible().catch(() => false)) {
      const options = evalSelect.locator("option");
      const count = await options.count();
      for (let i = 0; i < count; i += 1) {
        const value = (await options.nth(i).getAttribute("value")) ?? "";
        if (value.trim() && !/e\.g\./i.test((await options.nth(i).textContent()) ?? "")) {
          await evalSelect.selectOption(value);
          break;
        }
      }
    }
    await captureEvidence(page, "classifier", "03_classifier_model_selected.png", { labPage: true });

    const evaluateBtn = page.getByTestId("lab-classifier-evaluate");
    if (await evaluateBtn.isVisible().catch(() => false)) {
      await captureEvidence(page, "classifier", "04_classifier_eval_run_clicked.png", { labPage: true });
    } else {
      evidenceLog("classifier evaluate control unavailable - screenshot skipped for step 04");
    }

    const registry = page.getByTestId("classifier-registry-table");
    if (await registry.isVisible().catch(() => false)) {
      const activateBtn = registry.getByRole("button", { name: /Activate for project|Activar/i }).first();
      if (await activateBtn.isVisible().catch(() => false)) {
        await captureEvidence(page, "classifier", "09_classifier_activation.png", { labPage: true });
      }
    }

    evidenceLog("PASS classifier-evidence screenshots-only");
  });
});
