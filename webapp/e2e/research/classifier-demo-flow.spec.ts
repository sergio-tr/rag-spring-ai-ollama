import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createClassifierWorkbook } from "../fixtures/xlsx";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

test.describe.serial("Demo classifier flow @fullstack @demoHeavy", () => {
  test("train, evaluate, activate, classify, and expose selected model for chat @fullstack", async ({ page }) => {
    test.setTimeout(360_000);
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-classifier-demo"));
    await page.goto("/en/lab/classifier");

    await expect(page.getByText(/not configured|no.*configurad/i)).toHaveCount(0, { timeout: 20_000 });
    await expect(page.getByTestId("lab-classifier-train")).toBeEnabled({ timeout: 20_000 });

    const workbook = createClassifierWorkbook();
    const modelName = `e2e-clf-${Date.now()}`;
    await page.getByTestId("lab-classifier-train-model-name").fill(modelName);
    await page.getByTestId("lab-classifier-train-file").setInputFiles({
      name: "classifier-demo.xlsx",
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      buffer: workbook,
    });
    await page.getByTestId("lab-classifier-train-sync").check();
    await page.getByTestId("lab-classifier-train").click();

    const trainOutput = page.locator("pre").filter({ hasText: /modelId|metrics|accuracy/i }).first();
    await expect(trainOutput).toBeVisible({ timeout: 240_000 });
    await expect(trainOutput).toContainText(/modelId/i);

    await page.getByRole("button", { name: /Refresh|Actualizar|Refrescar/i }).last().click();
    const registry = page.getByTestId("classifier-registry-table");
    await expect(registry).toBeVisible({ timeout: 60_000 });
    const trainedRow = registry.locator("tr").filter({ hasText: modelName }).first();
    await expect(trainedRow).toBeVisible({ timeout: 60_000 });
    await trainedRow.getByRole("button", { name: /Activate|Activar/i }).click();
    await page.getByTestId("classifier-registry-confirm-activate").click();
    await expect(trainedRow.getByText(/ACTIVE|Activo/i)).toBeVisible({ timeout: 30_000 });

    const evalSelect = page.getByTestId("lab-classifier-eval-model");
    await expect(evalSelect).toBeEnabled({ timeout: 30_000 });
    const modelOption = evalSelect.locator("option").filter({ hasText: modelName }).first();
    await expect(modelOption).toHaveCount(1, { timeout: 30_000 });
    const modelTag = await modelOption.getAttribute("value");
    if (!modelTag) {
      throw new Error("Trained classifier option should expose inference tag.");
    }
    await evalSelect.selectOption(modelTag);
    await page.getByTestId("lab-classifier-eval-file").setInputFiles({
      name: "classifier-eval.xlsx",
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      buffer: workbook,
    });
    await page.getByTestId("lab-classifier-evaluate").click();
    await expect(page.locator("pre").filter({ hasText: /classificationReport|accuracy|confusion/i }).first())
      .toBeVisible({ timeout: 240_000 });

    await page.getByTestId("lab-classifier-classify-model").selectOption(modelTag);
    await page.getByTestId("lab-classifier-classify-query").fill("How many meetings mention the lift?");
    await page.getByTestId("lab-classifier-classify").click();
    await expect(page.locator("pre").filter({ hasText: /queryType|COUNT_DOCUMENTS/i }).first())
      .toBeVisible({ timeout: 60_000 });

    await page.getByRole("link", { name: /^chat$/i }).click();
    const panelButton = page.getByTestId("chat-config-trigger");
    await expect(panelButton).toBeVisible({ timeout: 20_000 });
    await panelButton.click();
    await expect(page.getByText(modelName).or(page.getByText(modelTag))).toBeVisible({ timeout: 30_000 });
  });
});
