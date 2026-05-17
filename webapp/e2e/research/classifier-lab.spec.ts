import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-08: Classifier lab smoke across CI modes. In e2e profile the registry may expose
 * deterministic seed models even when the Python classifier service is not part of the fast lane.
 */
test.describe("Lab classifier", () => {
  test("E2E-08 classifier page exposes configured or unavailable state @fullstack @critical", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/classifier");
    await expect(page.getByText(/classifier|clasificador/i).first()).toBeVisible({ timeout: 15_000 });

    const unavailableNotice = page.getByText(/not configured|no.*configurad/i).first();
    const registry = page.getByTestId("classifier-registry-table");
    await expect
      .poll(
        async () =>
          (await unavailableNotice.isVisible().catch(() => false)) ||
          (await registry.isVisible().catch(() => false)),
        { timeout: 15_000, intervals: [250, 500, 1000] },
      )
      .toBe(true);

    if (await unavailableNotice.isVisible().catch(() => false)) {
      await expect(page.getByTestId("lab-classifier-train")).toBeDisabled();
    } else {
      await expect(registry).toContainText(/default|classifier/i);
      await expect(page.getByTestId("lab-classifier-train")).toBeEnabled();
    }
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
