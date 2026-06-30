import { expect, test } from "@playwright/test";
import {
  addSmokeAccessCookie,
  installUxSurfacesApiStub,
  openChatConfiguration,
} from "./fixtures/ux-surfaces-api-stub";

test.describe("UX surfaces smoke @smoke", () => {
  test.beforeEach(async ({ page }) => {
    await installUxSurfacesApiStub(page);
    await addSmokeAccessCookie(page);
  });

  test("settings user config shows form fields, not raw JSON editors", async ({ page }) => {
    await page.goto("/en/settings/user", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByTestId("user-rag-config-form")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("rag-config-structured-form")).toBeVisible();
    await expect(page.getByTestId("user-account-preferences")).toBeVisible();
    await expect(page.getByLabel(/language/i)).toBeVisible();
    const technicalDetails = page.getByTestId("settings-model-parameters-advanced");
    const technicalSummary = technicalDetails.locator(":scope > summary");
    await expect(technicalSummary).toBeVisible();
    expect(await technicalDetails.evaluate((el) => (el as HTMLDetailsElement).open)).toBe(false);
    await expect(page.getByTestId("assistant-global-persona-input")).toBeVisible();
    await expect(page.getByLabel("Advanced configuration (JSON)")).not.toBeVisible();
    const mainText = await page.locator("main").innerText();
    expect(mainText).not.toMatch(/\{[\s\S]*"schemaVersion"/);

    await technicalSummary.click();
    const advancedJson = page.getByTestId("rag-config-advanced-json");
    await expect(advancedJson).toBeAttached();
    expect(await advancedJson.evaluate((el) => (el as HTMLDetailsElement).open)).toBe(false);
    await expect(advancedJson.locator("textarea")).not.toBeVisible();
  });

  test("chat config hides snapshot and profile hash until advanced is expanded", async ({ page }) => {
    await openChatConfiguration(page);
    await expect(page.getByTestId("chat-config-compact-summary")).toBeVisible({ timeout: 20_000 });
    const panel = page.locator("#chat-configuration-side-panel, [data-testid='chat-configuration-side-panel']");
    const mainText = (await panel.innerText().catch(() => "")) || (await page.locator("main").innerText());
    expect(mainText).not.toMatch(/Profile hash/i);
    expect(mainText).not.toMatch(/Active snapshot/i);
    expect(mainText).not.toMatch(/Effective keys:/i);
  });

  test("chat model selector is provider-aware", async ({ page }) => {
    await openChatConfiguration(page);
    await page.getByTestId("chat-config-edit-button").click({ timeout: 15_000 });
    await expect(page.getByTestId("chat-llm-model-provider")).toHaveText(/Configured model provider/i, {
      timeout: 15_000,
    });
  });

  test("chat preset selector uses human labels, not P-codes as primary text", async ({ page }) => {
    await openChatConfiguration(page);
    await page.getByTestId("chat-config-edit-button").click({ timeout: 15_000 });
    const presetSelect = page.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 15_000 });
    const options = await presetSelect.locator("option").allTextContents();
    expect(options.some((o) => /Chunk \+ metadata retrieval/i.test(o))).toBe(true);
    expect(options.every((o) => !/^P\d+\s*[—-]/.test(o.trim()))).toBe(true);
  });

  test("lab benchmark exports show primary JSON and CSV only by default", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem(
        "lab:evaluation-draft:v1:LLM_JUDGE_QA",
        JSON.stringify({ v: 1, lastEvaluationRunId: "smoke-run-id" }),
      );
    });

    await page.goto("/en/lab/evaluation/llm", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByTestId("lab-llm-eval-page")).toBeVisible({ timeout: 20_000 });
    const panel = page.getByTestId("lab-benchmark-results-panel");
    await expect(panel).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("lab-export-primary-json")).toBeVisible();
    await expect(page.getByTestId("lab-export-primary-csv")).toBeVisible();
    await expect(page.getByTestId("lab-export-mvp-csv")).not.toBeVisible();
    const advanced = page.getByTestId("lab-benchmark-export-advanced");
    await expect(advanced).toBeAttached();
    const advancedState = await advanced.evaluate((el) => {
      const details = el as HTMLDetailsElement;
      return {
        open: details.open,
        summaryText: details.querySelector("summary")?.textContent?.trim() ?? "",
      };
    });
    expect(advancedState.open).toBe(false);
    expect(advancedState.summaryText.length).toBeGreaterThan(0);
  });
});

test.describe("Admin catalog UX smoke @smoke", () => {
  test.beforeEach(async ({ page }) => {
    await installUxSurfacesApiStub(page, { admin: true });
    await addSmokeAccessCookie(page);
  });

  test("admin model catalog shows embedding compatibility state", async ({ page }) => {
    await page.goto("/en/admin", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByTestId("admin-models-card")).toBeVisible({ timeout: 30_000 });
    const catalogCard = page.getByTestId("admin-models-card");
    await expect(
      catalogCard.getByText(/Configured model catalog|Catálogo de modelos configurado/i),
    ).toBeVisible();
    await expect(page.getByTestId("admin-catalog-vector-compatible-wrong-dim-embed:latest")).toHaveText(/No/i);
    await expect(page.getByTestId("admin-catalog-incompatible-wrong-dim-embed:latest")).toBeVisible();
  });
});
