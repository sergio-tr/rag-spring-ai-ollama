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

    await page.locator("#cfile").setInputFiles({
      name: "e2e-train.xlsx",
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      buffer: Buffer.from("PK\u0003\u0004invalid-xlsx-for-e2e"),
    });
    await page.getByRole("button", { name: /^Train$|^Entrenar$/i }).click();

    await expect
      .poll(
        async () => {
          const alert = page.getByRole("alert");
          const progress = page.locator("pre");
          const alertText = (await alert.textContent().catch(() => "")) ?? "";
          const progressText = (await progress.textContent().catch(() => "")) ?? "";
          const combined = `${alertText} ${progressText}`;
          return (
            /fail|error|clasificador|classifier|not configured|unavailable|502|503|504/i.test(combined) ||
            /done|success|complete|succeeded/i.test(combined)
          );
        },
        { timeout: 120_000 },
      )
      .toBe(true);
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
