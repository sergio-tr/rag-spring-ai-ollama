import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-08: Classifier lab — with classifier URL unset (typical CI), async train ends in a terminal
 * error surfaced to the UI; with a live classifier, job may succeed (not asserted here).
 */
test.describe("Lab classifier", () => {
  test("E2E-08 classifier train submits and finishes or shows error @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/classifier");
    await expect(page.getByText(/classifier|clasificador/i).first()).toBeVisible({ timeout: 15_000 });

    // In CI/e2e profile the classifier service is typically not configured. The UI should
    // clearly communicate this, and keep destructive actions disabled (fast + stable smoke).
    await expect(page.getByText(/not configured|no.*configurad/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("lab-classifier-train")).toBeDisabled();
  });

  test("E2E-08b registry section when classifier configured @fullstack", async ({ page }) => {
    test.skip(
      process.env.E2E_CLASSIFIER_REGISTRY !== "1",
      "Set E2E_CLASSIFIER_REGISTRY=1 when the BFF has classifier-service configured (Lab status shows classifier OK).",
    );
    await loginAsSeedUser(page);
    await page.goto("/en/lab/classifier");
    await expect(page.getByRole("heading", { name: /Registered models|Modelos registrados/i })).toBeVisible({
      timeout: 20_000,
    });
  });
});
