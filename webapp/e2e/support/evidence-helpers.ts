import * as crypto from "node:crypto";
import * as fs from "node:fs";
import * as path from "node:path";
import { expect, type Page } from "@playwright/test";
import {
  assertNoForbiddenLabCopy,
  fetchSelectableEmbeddingModelIds,
  selectEmbeddingModelsByIds,
} from "./lab-helpers";
import { bootstrapBrowserSession, gotoWithProxyRetry, productApiUrl } from "./helpers";

export const PHASE2_EVIDENCE_ROOT = path.resolve(
  __dirname,
  "../../../.cursor/evidence/phase-2-screenshots-and-embedding-campaign-20250701",
);

export const THESIS_EMBEDDING_MODEL_IDS = [
  "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
  "mxbai-embed-large",
  "bge-m3",
  "snowflake-arctic-embed2",
] as const;

const FORBIDDEN_PROVIDER_LABELS = [/OPENAI_COMPATIBLE/, /OLLAMA_NATIVE/];

const FORBIDDEN_CATALOG_LABELS = [
  /\bDemo_Best\b/,
  /\bDemo_Worst\b/,
  /\bP0\b/,
  /\bP1\b/,
  /\bP3\b/,
  /\bP8\b/,
  /\bP10\b/,
  /\bP15\b/,
  ...FORBIDDEN_PROVIDER_LABELS,
];

const LLM_MODEL_GROUPS: Record<string, readonly string[]> = {
  small: ["deepseek-r1:1.5b", "llama3.2:3b", "qwen3.5:2b", "qwen3.5:4b", "gemma4:e4b"],
  "small-medium": ["deepseek-r1:7b", "gemma4:12b", "qwen3.5:9b"],
  medium: ["deepseek-v2:16b", "gpt-oss:20b", "gemma4:26b", "qwen3.6:27b"],
};

export function evidenceDir(subdir: string): string {
  return path.join(PHASE2_EVIDENCE_ROOT, subdir);
}

export function shouldRunCampaigns(): boolean {
  return process.env.RUN_EVIDENCE_CAMPAIGNS === "true";
}

export function screenshotsOnly(): boolean {
  return process.env.EVIDENCE_SCREENSHOTS_ONLY !== "false";
}

export function waitForCompletion(): boolean {
  return process.env.EVIDENCE_WAIT_FOR_COMPLETION === "true";
}

export function evidenceModelGroup(): string {
  return process.env.EVIDENCE_MODEL_GROUP ?? "small";
}

export function evidenceLogPath(): string {
  return path.join(evidenceDir("raw_logs"), "e2e-evidence.log");
}

export function evidenceLog(line: string): void {
  const logPath = evidenceLogPath();
  fs.mkdirSync(path.dirname(logPath), { recursive: true });
  fs.appendFileSync(logPath, `${new Date().toISOString()} ${line}\n`, "utf8");
}

export function writeEvidenceJson(subdir: string, filename: string, payload: unknown): void {
  const dir = evidenceDir(subdir);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, filename), `${JSON.stringify(payload, null, 2)}\n`, "utf8");
}

export function sha256File(filePath: string): string {
  const data = fs.readFileSync(filePath);
  return crypto.createHash("sha256").update(data).digest("hex");
}

export async function loginAsDevAdmin(page: Page): Promise<void> {
  const email = process.env.E2E_ADMIN_EMAIL ?? "admin@dev.local";
  const password = process.env.E2E_ADMIN_PASSWORD ?? "dev";
  const loginRes = await page.request.post(productApiUrl("/auth/login"), {
    data: { email, password },
    headers: { "Content-Type": "application/json" },
  });
  expect(loginRes.ok(), `admin login failed: ${await loginRes.text()}`).toBeTruthy();
  await bootstrapBrowserSession(page, (await loginRes.json()) as { accessToken: string; refreshToken?: string });
}

export async function collapseAdvancedTechnicalDetails(page: Page): Promise<void> {
  const details = page.locator("details").filter({ hasText: /Advanced technical details/i });
  const count = await details.count();
  for (let i = 0; i < count; i += 1) {
    const el = details.nth(i);
    if (await el.getAttribute("open")) {
      await el.locator("summary").click().catch(() => undefined);
    }
  }
}

async function assertCommonScreenshotGuards(page: Page): Promise<void> {
  await collapseAdvancedTechnicalDetails(page);
  const bodyText = await page.locator("body").innerText();
  for (const re of FORBIDDEN_PROVIDER_LABELS) {
    expect(bodyText, `Forbidden provider label matched ${re}`).not.toMatch(re);
  }
  await expect(page.getByText(/internal server error|500\b|something went wrong/i)).toHaveCount(0);
  await expect(page.getByRole("alert").filter({ hasText: /crash|fatal|5\d{2}/i })).toHaveCount(0);
}

export async function assertPreScreenshotGuards(page: Page): Promise<void> {
  await assertNoForbiddenLabCopy(page);
  await assertCommonScreenshotGuards(page);
  const bodyText = await page.locator("body").innerText();
  for (const re of FORBIDDEN_CATALOG_LABELS) {
    expect(bodyText, `Forbidden primary label matched ${re}`).not.toMatch(re);
  }
}

export async function assertLabScreenshotGuards(page: Page): Promise<void> {
  await assertNoForbiddenLabCopy(page);
  await assertCommonScreenshotGuards(page);
}

export async function captureEvidence(
  page: Page,
  subdir: string,
  filename: string,
  options?: { fullPage?: boolean; element?: ReturnType<Page["locator"]>; labPage?: boolean },
): Promise<string> {
  if (options?.labPage) {
    await assertLabScreenshotGuards(page);
  } else {
    await assertPreScreenshotGuards(page);
  }
  const dir = path.join(evidenceDir("screenshots"), subdir);
  fs.mkdirSync(dir, { recursive: true });
  const filePath = path.join(dir, filename);
  if (options?.element) {
    await options.element.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: options?.fullPage ?? true });
  }
  evidenceLog(`screenshot ${subdir}/${filename}`);
  return filePath;
}

export async function selectThesisEmbeddingModels(page: Page): Promise<string[]> {
  const apiSelectable = await fetchSelectableEmbeddingModelIds(page).catch(() => [] as string[]);
  const selected: string[] = [];
  for (const modelId of THESIS_EMBEDDING_MODEL_IDS) {
    const byTestId = page.getByTestId(`lab-benchmark-embedding-models-${modelId}`);
    const byLabel = page.getByRole("checkbox", { name: modelId, exact: true });
    const box = (await byTestId.isVisible().catch(() => false)) ? byTestId : byLabel;
    if (await box.isVisible().catch(() => false)) {
      await box.check();
      selected.push(modelId);
      continue;
    }
    if (apiSelectable.includes(modelId)) {
      selected.push(modelId);
    }
  }
  if (selected.length < THESIS_EMBEDDING_MODEL_IDS.length && apiSelectable.length >= THESIS_EMBEDDING_MODEL_IDS.length) {
    await selectEmbeddingModelsByIds(page, [...THESIS_EMBEDDING_MODEL_IDS]);
    return [...THESIS_EMBEDDING_MODEL_IDS];
  }
  expect(
    selected.length,
    `Need all 4 thesis embedding models in UI. Found: [${selected.join(", ")}]. API: [${apiSelectable.join(", ")}]`,
  ).toBe(THESIS_EMBEDDING_MODEL_IDS.length);
  return selected;
}

export function llmModelsForGroup(group: string, selectable: string[]): string[] {
  const preferred = LLM_MODEL_GROUPS[group] ?? LLM_MODEL_GROUPS.small;
  const picked = preferred.filter((id) => selectable.includes(id));
  if (picked.length >= 2) return picked.slice(0, Math.min(3, picked.length));
  return selectable.slice(0, Math.min(3, selectable.length));
}

export async function gotoEvidenceRoute(page: Page, route: string): Promise<void> {
  await gotoWithProxyRetry(page, route);
  await page.waitForLoadState("domcontentloaded");
}
