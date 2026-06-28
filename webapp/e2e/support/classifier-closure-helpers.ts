import { expect, type Page } from "@playwright/test";
import { createClassifierWorkbook } from "../fixtures/xlsx";

export type ClassifierLabRunResult = {
  modelName: string;
  modelTag: string;
};

/** Opens LAB classifier and waits until train controls are ready. */
export async function openLabClassifierPage(page: Page): Promise<void> {
  await page.goto("/en/lab/classifier", { waitUntil: "domcontentloaded", timeout: 20_000 });
  await expect(page.getByText(/not configured|no.*configurad/i)).toHaveCount(0, { timeout: 20_000 });
  await expect(page.getByTestId("lab-classifier-train")).toBeEnabled({ timeout: 20_000 });
}

/** Trains a classifier model; returns display name and inference tag. */
export async function trainClassifierModel(
  page: Page,
  modelName?: string,
): Promise<ClassifierLabRunResult> {
  const workbook = createClassifierWorkbook();
  const name = modelName ?? `e2e-clf-${Date.now()}`;
  await page.getByTestId("lab-classifier-train-model-name").fill(name);
  await page.getByTestId("lab-classifier-train-file").setInputFiles({
    name: "classifier-closure.xlsx",
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
  const trainedRow = registry.locator("tr").filter({ hasText: name }).first();
  await expect(trainedRow).toBeVisible({ timeout: 60_000 });

  const evalSelect = page.getByTestId("lab-classifier-eval-model");
  await expect(evalSelect).toBeEnabled({ timeout: 30_000 });
  const modelOption = evalSelect.locator("option").filter({ hasText: name }).first();
  await expect(modelOption).toHaveCount(1, { timeout: 30_000 });
  const modelTag = await modelOption.getAttribute("value");
  if (!modelTag?.trim()) {
    throw new Error("Trained classifier option should expose inference tag.");
  }

  return { modelName: name, modelTag: modelTag.trim() };
}

/** Evaluates the trained model in LAB. */
export async function evaluateClassifierModel(page: Page, modelTag: string): Promise<void> {
  const workbook = createClassifierWorkbook();
  await page.getByTestId("lab-classifier-eval-model").selectOption(modelTag);
  await page.getByTestId("lab-classifier-eval-file").setInputFiles({
    name: "classifier-eval-closure.xlsx",
    mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    buffer: workbook,
  });
  await page.getByTestId("lab-classifier-evaluate").click();
  await expect(
    page.locator("pre").filter({ hasText: /classificationReport|accuracy|confusion/i }).first(),
  ).toBeVisible({ timeout: 240_000 });
}

/** Activates the trained model row in the registry (ACTIVE badge). */
export async function activateClassifierModel(page: Page, modelName: string): Promise<void> {
  const registry = page.getByTestId("classifier-registry-table");
  const trainedRow = registry.locator("tr").filter({ hasText: modelName }).first();
  await expect(trainedRow).toBeVisible({ timeout: 60_000 });
  await trainedRow.getByRole("button", { name: /Activate|Activar/i }).click();
  await page.getByTestId("classifier-registry-confirm-activate").click();
  await expect(trainedRow.getByText(/ACTIVE|Activo/i)).toBeVisible({ timeout: 30_000 });
}

/** Inline LAB classify sanity check (valid query type, not INVALID_OUTPUT). */
export async function classifyInLab(page: Page, modelTag: string, query: string): Promise<void> {
  await page.getByTestId("lab-classifier-classify-model").selectOption(modelTag);
  await page.getByTestId("lab-classifier-classify-query").fill(query);
  await page.getByTestId("lab-classifier-classify").click();
  const output = page.locator("pre").filter({ hasText: /queryType|COUNT_DOCUMENTS/i }).first();
  await expect(output).toBeVisible({ timeout: 60_000 });
  await expect(output).not.toContainText(/INVALID_OUTPUT/i);
}

/** Ollama HTTP base on the host (demo stack binds container to :11434). */
function ollamaHostBaseUrl(): string {
  return (process.env.E2E_OLLAMA_INTERNAL_URL ?? "http://127.0.0.1:11434").replace(/\/$/, "");
}

/** True when actuator and a minimal Ollama generate succeed (health alone is not enough on CPU). */
export async function isOllamaUpForChat(page: Page): Promise<boolean> {
  const base =
    process.env.PLAYWRIGHT_BASE_URL ?? process.env.E2E_PUBLIC_BASE_URL ?? "https://127.0.0.1:8444";
  const healthUrl = `${base.replace(/\/$/, "")}/actuator/health`;
  try {
    const res = await page.request.get(healthUrl, {
      timeout: 8_000,
      ignoreHTTPSErrors: true,
    });
    if (res.status() !== 200) {
      return false;
    }
    const body = (await res.json()) as { components?: { ollama?: { status?: string } } };
    if (body.components?.ollama?.status !== "UP") {
      return false;
    }
  } catch {
    return false;
  }

  const chatModel = process.env.SPRING_AI_OLLAMA_CHAT_MODEL ?? "gemma3:4b";
  try {
    const probe = await page.request.post(`${ollamaHostBaseUrl()}/api/generate`, {
      data: { model: chatModel, prompt: "OK", stream: false, options: { num_predict: 5 } },
      timeout: 60_000,
    });
    if (!probe.ok()) {
      return false;
    }
    const gen = (await probe.json()) as { error?: string; response?: string };
    return !gen.error && Boolean(gen.response?.trim());
  } catch {
    return false;
  }
}
