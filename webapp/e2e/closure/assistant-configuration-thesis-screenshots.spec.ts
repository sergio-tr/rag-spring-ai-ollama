import { expect, test, type Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import {
  bootstrapBrowserSession,
  createNewChatConversation,
  gotoWithProxyRetry,
  loginAsSeedUser,
  openChatForProject,
  productApiUrl,
} from "../support/helpers";
import { gotoLabEvaluationPage } from "../support/lab-helpers";

const SCREENSHOT_DIR = path.resolve(
  process.cwd(),
  "../.cursor/evidence/assistant-configuration-closure-20260629/thesis-screenshots",
);

const SEED_PROJECT_ID = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22";
const VIEWPORT = { width: 1440, height: 900 };

async function capture(page: Page, fileName: string): Promise<void> {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, fileName), fullPage: false });
}

async function loginAsDevAdmin(page: Page): Promise<void> {
  const email = process.env.E2E_ADMIN_EMAIL ?? "admin@dev.local";
  const password = process.env.E2E_ADMIN_PASSWORD ?? "dev";
  const loginRes = await page.request.post(productApiUrl("/auth/login"), {
    data: { email, password },
    headers: { "Content-Type": "application/json" },
  });
  expect(loginRes.ok(), `admin API login failed: ${loginRes.status()} ${await loginRes.text()}`).toBeTruthy();
  await bootstrapBrowserSession(page, (await loginRes.json()) as { accessToken: string; refreshToken?: string });
}

async function activateSeedProject(page: Page): Promise<void> {
  await gotoWithProxyRetry(page, "/en/projects");
  const card = page.locator('[data-slot="card"]').filter({ hasText: /Default project/i }).first();
  await expect(card).toBeVisible({ timeout: 15_000 });
  const activeMarker = card.getByRole("button", { name: /^(Active|Activo)$/i });
  if (await activeMarker.isVisible().catch(() => false)) {
    return;
  }
  await card.getByRole("button", { name: /set active only|activar solo/i }).click();
  await expect(activeMarker).toBeVisible({ timeout: 15_000 });
}

async function openChatConfigSummary(page: Page): Promise<void> {
  const trigger = page.getByTestId("chat-config-trigger");
  await expect(trigger).toBeEnabled({ timeout: 30_000 });
  await trigger.click();
  await expect(page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"))).toBeVisible({
    timeout: 15_000,
  });
}

test.describe("Assistant configuration thesis screenshots @closure @evidence", () => {
  test.use({ viewport: VIEWPORT, colorScheme: "light" });

  test("capture 11 thesis-safe PNGs", async ({ page }) => {
    test.setTimeout(900_000);

    await loginAsSeedUser(page);
    await activateSeedProject(page);
    await openChatForProject(page, SEED_PROJECT_ID);
    await createNewChatConversation(page, { projectId: SEED_PROJECT_ID });
    await openChatConfigSummary(page);
    await expect(page.getByTestId("chat-config-compact-summary")).toBeVisible();
    await capture(page, "01_assistant_configuration_overview.png");

    await gotoWithProxyRetry(page, "/en/settings/user");
    await expect(page.getByText(/Assistant instructions/i).first()).toBeVisible({ timeout: 15_000 });
    await capture(page, "02_assistant_instructions.png");

    const modelSection = page.getByRole("heading", { name: /Model configuration/i, level: 4 });
    await modelSection.scrollIntoViewIfNeeded();
    await expect(modelSection).toBeVisible({ timeout: 10_000 });
    await capture(page, "03_model_configuration.png");

    const retrievalSection = page.getByRole("heading", { name: /Retrieval settings/i, level: 4 });
    await retrievalSection.scrollIntoViewIfNeeded();
    await expect(retrievalSection).toBeVisible({ timeout: 10_000 });
    await capture(page, "04_retrieval_settings.png");

    await openChatForProject(page, SEED_PROJECT_ID);
    await createNewChatConversation(page, { projectId: SEED_PROJECT_ID });
    let configPanel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
    if (!(await configPanel.isVisible().catch(() => false))) {
      await openChatConfigSummary(page);
      configPanel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
    }
    await expect(configPanel).toBeVisible({ timeout: 15_000 });
    const memorySection = configPanel.getByText(/Conversation memory/i).first();
    await memorySection.scrollIntoViewIfNeeded();
    const clarificationSection = configPanel.getByText(/Clarification/i).first();
    if (await clarificationSection.isVisible().catch(() => false)) {
      await clarificationSection.scrollIntoViewIfNeeded();
    }
    await capture(page, "05_conversation_memory_and_clarification.png");

    await gotoWithProxyRetry(page, "/en/lab");
    await expect(page.getByRole("heading", { name: /evaluation lab|research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    await capture(page, "06_evaluation_setup.png");

    await gotoLabEvaluationPage(page, "llm");
    await expect(page.getByRole("main")).toBeVisible({ timeout: 15_000 });
    await capture(page, "07_single_model_evaluation.png");

    const resultsRegion = page.getByTestId("lab-evaluation-results").or(page.locator("main"));
    await resultsRegion.first().scrollIntoViewIfNeeded();
    const exportBtn = page.getByRole("button", { name: /export/i }).first();
    if (await exportBtn.isVisible().catch(() => false)) {
      await exportBtn.scrollIntoViewIfNeeded();
    }
    await capture(page, "08_evaluation_results_exports.png");

    await loginAsDevAdmin(page);
    await gotoWithProxyRetry(page, "/en/admin");
    await expect(page.getByText(/Configured model catalog|catálogo de modelos configurado/i).first()).toBeVisible({
      timeout: 20_000,
    });
    await capture(page, "09_admin_model_catalog.png");

    await loginAsSeedUser(page);
    await activateSeedProject(page);
    await openChatForProject(page, SEED_PROJECT_ID);
    await createNewChatConversation(page, { projectId: SEED_PROJECT_ID });
    await openChatConfigSummary(page);
    const panel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
    await panel.getByTestId("chat-config-edit-button").click();
    const documentsSection = panel.getByText(/Source documents/i).first();
    await documentsSection.scrollIntoViewIfNeeded();
    await expect(documentsSection).toBeVisible({ timeout: 10_000 });
    await capture(page, "10_source_documents_evidence.png");

    await gotoWithProxyRetry(page, "/en/settings/user");
    const settingsAdvanced = page.getByText(/Advanced technical details/i).last();
    await settingsAdvanced.scrollIntoViewIfNeeded();
    await settingsAdvanced.click();
    await expect(page.getByText(/Advanced technical details/i).first()).toBeVisible();
    await capture(page, "appendix_advanced_technical_details.png");
  });

  test("capture remaining thesis screenshots @closure @evidence @partial", async ({ page }) => {
    test.setTimeout(300_000);
    await loginAsSeedUser(page);
    await activateSeedProject(page);
    await openChatForProject(page, SEED_PROJECT_ID);
    await createNewChatConversation(page, { projectId: SEED_PROJECT_ID });
    await openChatConfigSummary(page);
    const panel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
    await panel.getByTestId("chat-config-edit-button").click();
    const documentsSection = panel.getByText(/Source documents/i).first();
    await documentsSection.scrollIntoViewIfNeeded();
    await expect(documentsSection).toBeVisible({ timeout: 10_000 });
    await capture(page, "10_source_documents_evidence.png");

    await gotoWithProxyRetry(page, "/en/settings/user");
    const settingsAdvanced = page.getByText(/Advanced technical details/i).last();
    await settingsAdvanced.scrollIntoViewIfNeeded();
    await settingsAdvanced.click();
    await expect(page.getByText(/Advanced technical details/i).first()).toBeVisible();
    await capture(page, "appendix_advanced_technical_details.png");
  });
});
