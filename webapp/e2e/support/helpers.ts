import { expect, type Locator, type Page } from "@playwright/test";
import { adminEmail, adminPassword, seedEmail, seedPassword } from "../fixtures/users";

/**
 * Runs before the first document load on this page so client UI does not start in a state where
 * chat actions are hidden (persisted rail collapse + conversation list collapse).
 */
async function registerE2eLayoutPersistenceReset(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      sessionStorage.removeItem("chat-conv-list-collapsed");
    } catch {
      /* ignore */
    }
    try {
      const raw = localStorage.getItem("rag-sidebar");
      if (!raw) return;
      const o = JSON.parse(raw) as Record<string, unknown>;
      if (!o || typeof o !== "object") return;
      o.shellCollapsed = false;
      localStorage.setItem("rag-sidebar", JSON.stringify(o));
    } catch {
      /* ignore */
    }
  });
}

const apiBase = () =>
  (
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    process.env.API_BASE_URL ??
    process.env.INTEGRATION_BACKEND_URL ??
    "http://127.0.0.1:9000"
  ).replace(/\/$/, "");
const directApiBase = () =>
  (
    process.env.INTEGRATION_BACKEND_URL ??
    process.env.API_BASE_URL ??
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    "http://127.0.0.1:9000"
  ).replace(/\/$/, "");
const productPrefix = () =>
  (process.env.NEXT_PUBLIC_RAG_API_PREFIX ?? "/api/v5").replace(/\/$/, "");
const loginTimeoutMs = Number.parseInt(process.env.E2E_LOGIN_TIMEOUT_MS ?? "12000", 10);

type LoginResponse = {
  accessToken: string;
  refreshToken?: string;
};

/** Full URL for product API path (e.g. `/projects`). */
export function productApiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${apiBase()}${productPrefix()}${p}`;
}

function directProductApiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${directApiBase()}${productPrefix()}${p}`;
}

export async function authHeadersFromPage(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem("rag_access_token"));
  const h: Record<string, string> = { Accept: "application/json" };
  if (token) {
    h.Authorization = `Bearer ${token}`;
  }
  return h;
}

function activeProjectIdFromUrl(page: Page): string {
  const u = new URL(page.url());
  const pid = u.searchParams.get("projectId");
  if (!pid) {
    throw new Error(`Expected ?projectId= on URL but got: ${page.url()}`);
  }
  return pid;
}

type ProjectDocumentDto = {
  id: string;
  fileName: string;
  status: "INGESTING" | "READY" | "ERROR";
  errorMessage?: string | null;
};

type ConversationDto = {
  id: string;
};

type ProjectDto = {
  id: string;
  name: string;
};

async function findProjectIdByName(page: Page, projectName: string): Promise<string> {
  const res = await page.request.get(directProductApiUrl("/projects?page=0&size=100"), {
    headers: await authHeadersFromPage(page),
  });
  expect(res.status(), await res.text()).toBe(200);
  const body = await res.json();
  const rows = Array.isArray(body)
    ? body
    : Array.isArray(body?.items)
      ? body.items
      : Array.isArray(body?.content)
        ? body.content
        : [];
  const project = (rows as ProjectDto[]).find((p) => p.name === projectName);
  if (!project?.id) {
    throw new Error(`Project not found after creation: ${projectName}`);
  }
  return project.id;
}

/**
 * Polls the product API for a document to become READY (by filename) under the active projectId in the URL.
 * This avoids flakiness where the documents table UI refresh lags behind the backend ingest completion.
 */
export async function waitForDocumentReadyByName(
  page: Page,
  fileName: string,
  timeoutMs: number,
): Promise<ProjectDocumentDto> {
  const projectId = activeProjectIdFromUrl(page);
  const headers = await authHeadersFromPage(page);
  const started = Date.now();

  // Small backoff sequence; Playwright already runs with 1 worker in CI for fullstack.
  const intervals = [200, 400, 800, 1200, 2000];
  let intervalIdx = 0;

  while (Date.now() - started < timeoutMs) {
    const res = await page.request.get(productApiUrl(`/projects/${projectId}/documents`), { headers });
    if (!res.ok()) {
      const body = await res.text();
      throw new Error(
        `waitForDocumentReadyByName: GET /projects/${projectId}/documents failed ${res.status()} ${body}`,
      );
    }
    const docs = (await res.json()) as ProjectDocumentDto[];
    const doc = docs.find((d) => d.fileName === fileName);
    if (doc) {
      if (doc.status === "READY") return doc;
      if (doc.status === "ERROR") {
        throw new Error(
          `waitForDocumentReadyByName: document reached ERROR (file=${fileName} id=${doc.id}) ${doc.errorMessage ?? ""}`,
        );
      }
    }

    const sleepMs = intervals[Math.min(intervalIdx, intervals.length - 1)];
    intervalIdx += 1;
    await page.waitForTimeout(sleepMs);
  }

  throw new Error(`waitForDocumentReadyByName: timed out after ${timeoutMs}ms waiting for READY (${fileName})`);
}

/** Logs in with Flyway seed credentials and waits for the projects page. */
export async function loginAsSeedUser(page: Page): Promise<void> {
  await registerE2eLayoutPersistenceReset(page);
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const reachedLogin = await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 10_000 })
      .then(() => true)
      .catch(() => false);
    if (!reachedLogin) {
      if (attempt === 1) {
        await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 10_000 });
      }
      continue;
    }
    await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => undefined);
    const emailInput = page.getByLabel(/email|correo/i);
    const passwordInput = page.getByLabel(/^password$/i);
    await expect(emailInput).toBeVisible({ timeout: loginTimeoutMs });
    await expect(emailInput).toBeEnabled({ timeout: loginTimeoutMs });
    await expect(passwordInput).toBeVisible({ timeout: loginTimeoutMs });
    await expect(passwordInput).toBeEnabled({ timeout: loginTimeoutMs });
    await emailInput.fill(seedEmail());
    await passwordInput.fill(seedPassword());
    await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
    if (await page.waitForURL(/\/en\/projects/, { timeout: loginTimeoutMs }).then(() => true).catch(() => false)) {
      break;
    }
  }
  if (!/\/en\/projects/.test(page.url())) {
    const loginRes = await page.request.post(`${apiBase()}/api/auth/login`, {
      data: { email: seedEmail(), password: seedPassword() },
      headers: { "Content-Type": "application/json" },
    });
    expect(loginRes.ok(), `seed API login failed: ${loginRes.status()} ${await loginRes.text()}`).toBeTruthy();
    const tokens = (await loginRes.json()) as LoginResponse;
    expect(tokens.accessToken, "seed API login access token").toBeTruthy();
    await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 10_000 });
    await page.evaluate(async ({ accessToken, refreshToken }) => {
      const sessionRes = await fetch("/api/auth/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ accessToken, refreshToken }),
      });
      if (!sessionRes.ok) {
        throw new Error(`session cookie failed: ${sessionRes.status}`);
      }
      sessionStorage.setItem("rag_access_token", accessToken);
    }, tokens);
    await page.goto("/en/projects", { waitUntil: "domcontentloaded", timeout: 10_000 });
  }
  await expect(
    page.getByRole("heading", { name: /^projects$/i }),
  ).toBeVisible({ timeout: loginTimeoutMs });
}

/**
 * Creates a project via dialog; backend activate + client store mark it active.
 * Waits until the new card shows the Active / Activo state (no extra click).
 */
export async function createAndActivateProject(page: Page, projectName: string): Promise<string> {
  const mainNewProject = page.locator("main").getByRole("button", { name: /new project|nuevo proyecto/i }).first();
  const newProjectTrigger = (await mainNewProject.isVisible().catch(() => false))
    ? mainNewProject
    : page.getByRole("button", { name: /new project|nuevo proyecto/i }).first();
  await expect(newProjectTrigger).toBeEnabled({ timeout: 15_000 });
  await newProjectTrigger.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await dialog.locator("#proj-name").fill(projectName);
  const metadataIndex = dialog.getByLabel(/metadata index/i);
  if (await metadataIndex.isVisible().catch(() => false)) {
    await metadataIndex.check();
  }
  const embeddingModelInput = dialog.getByLabel(/embedding model id/i);
  if (await embeddingModelInput.isVisible().catch(() => false)) {
    await embeddingModelInput.fill("mxbai-embed-large");
  }
  await dialog.getByRole("button", { name: /^(create|crear)$/i }).click();
  await expect(dialog).not.toBeVisible({ timeout: 20_000 });
  const projectCard = page.locator('[data-slot="card"]').filter({ hasText: projectName }).first();
  await expect(projectCard).toBeVisible({ timeout: 20_000 });
  // Require the actual active marker — not "Set active only", which also contains the substring "active".
  await expect(projectCard.getByRole("button", { name: /^(Active|Activo)$/i })).toBeVisible({
    timeout: 20_000,
  });
  return findProjectIdByName(page, projectName);
}

/** ADMIN user seeded when Spring profile {@code e2e} is active (see E2eAdminUserSeeder). */
export async function loginAsE2eAdmin(page: Page): Promise<void> {
  await registerE2eLayoutPersistenceReset(page);
  await page.goto("/en/login", { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => undefined);
  const emailInput = page.getByLabel(/email|correo/i);
  const passwordInput = page.getByLabel(/^password$/i);
  await expect(emailInput).toBeVisible({ timeout: loginTimeoutMs });
  await expect(emailInput).toBeEnabled({ timeout: loginTimeoutMs });
  await expect(passwordInput).toBeVisible({ timeout: loginTimeoutMs });
  await expect(passwordInput).toBeEnabled({ timeout: loginTimeoutMs });
  await emailInput.fill(adminEmail());
  await passwordInput.fill(adminPassword());
  await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
  await expect(page).toHaveURL(/\/en\/projects/, { timeout: loginTimeoutMs });
}

export type SendChatMessageOptions = {
  /** Wait for the composer textarea to become visible and enabled. Default 15000. */
  textareaReadyTimeoutMs?: number;
  /** Wait for Send to become enabled after filling the textarea. Default 15000. */
  sendEnabledTimeoutMs?: number;
};

export async function openChatConfigurationPanel(page: Page): Promise<Locator> {
  const visiblePanel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
  if (await visiblePanel.isVisible().catch(() => false)) {
    return visiblePanel;
  }
  const trigger = page.getByTestId("chat-config-trigger");
  await expect(trigger).toBeVisible({ timeout: 15_000 });
  await expect(trigger).toBeEnabled({ timeout: 15_000 });
  await trigger.click();
  const openedPanel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
  await expect(openedPanel).toBeVisible({ timeout: 15_000 });
  return openedPanel;
}

export async function openChatDocumentsSheet(page: Page): Promise<Locator> {
  const panel = await openChatConfigurationPanel(page);
  await panel.getByTestId("chat-open-documents-sheet").click();
  const sheet = page.getByTestId("chat-documents-sheet");
  await expect(sheet).toBeVisible({ timeout: 15_000 });
  return sheet;
}

export async function createNewChatConversation(page: Page): Promise<void> {
  const newConversation = page.getByTestId("chat-new-conversation");
  await expect(newConversation).toBeEnabled({ timeout: 15_000 });
  await newConversation.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  const presetSelect = dialog.getByLabel(/initial preset/i);
  await expect(presetSelect).toBeVisible({ timeout: 15_000 });
  const compatibleChunkPreset = presetSelect.locator("option").filter({ hasText: /P3 .*chunk-level dense retrieval/i }).first();
  if ((await compatibleChunkPreset.count()) > 0 && !(await compatibleChunkPreset.isDisabled())) {
    const value = await compatibleChunkPreset.getAttribute("value");
    if (value) {
      await presetSelect.selectOption(value);
    }
  }
  await dialog.getByRole("button", { name: /^(create conversation|create|crear conversación|crear)$/i }).click();
  await expect(dialog).not.toBeVisible({ timeout: 20_000 });
  const routed = await page.waitForURL(/[?&]conversationId=[a-f0-9-]{36}/i, { timeout: 20_000 })
    .then(() => true)
    .catch(() => false);
  let conversationId = new URL(page.url()).searchParams.get("conversationId");
  if (!routed && !conversationId) {
    const projectId = activeProjectIdFromUrl(page);
    const res = await page.request.get(directProductApiUrl(`/projects/${projectId}/conversations`), {
      headers: await authHeadersFromPage(page),
    });
    expect(res.status(), await res.text()).toBe(200);
    const conversations = (await res.json()) as ConversationDto[];
    conversationId = conversations[0]?.id ?? null;
    if (conversationId) {
      await page.goto(`/en/chat?projectId=${projectId}&conversationId=${conversationId}`, {
        waitUntil: "domcontentloaded",
      });
    }
  }
  if (!conversationId) {
    throw new Error(`createNewChatConversation: missing conversationId in URL ${page.url()}`);
  }
  await expect(page.getByTestId(`conversation-item-${conversationId}`)).toBeVisible({ timeout: 20_000 });
}

function chatComposerLocators(page: Page): {
  textarea: Locator;
  sendButton: Locator;
  newConversationButton: Locator;
} {
  const column = page.getByTestId("chat-readable-column");
  return {
    textarea: column.getByTestId("chat-message-composer"),
    sendButton: column.getByTestId("chat-send-button"),
    newConversationButton: page.getByTestId("chat-new-conversation"),
  };
}

/**
 * Waits for the chat composer, fills the message, and submits via Send (or Enter as fallback).
 * Retries with clear/refill and a single "new conversation" reset when the Send button stays disabled.
 */
export async function sendChatMessage(page: Page, message: string, options?: SendChatMessageOptions): Promise<void> {
  const trimmed = message.trim();
  if (!trimmed) {
    throw new Error("sendChatMessage: message must be non-empty after trim.");
  }

  const textareaReadyTimeoutMs = options?.textareaReadyTimeoutMs ?? 15_000;
  const sendEnabledTimeoutMs = options?.sendEnabledTimeoutMs ?? 15_000;
  const recoverySendTimeoutMs = 10_000;
  const afterEnterEmptyTimeoutMs = 8_000;

  const { textarea, sendButton, newConversationButton } = chatComposerLocators(page);

  async function prepareComposer(): Promise<void> {
    if (page.isClosed()) {
      throw new Error("sendChatMessage: page is already closed.");
    }
    await expect(textarea).toBeVisible({ timeout: textareaReadyTimeoutMs });
    await expect(textarea).toBeEnabled({ timeout: textareaReadyTimeoutMs });
    await textarea.fill(message);
  }

  async function clickSendWhenEnabled(timeoutMs: number): Promise<boolean> {
    try {
      await expect(sendButton).toBeEnabled({ timeout: timeoutMs });
      await sendButton.click();
      return true;
    } catch {
      return false;
    }
  }

  async function submitViaEnterAndConfirm(): Promise<boolean> {
    await textarea.press("Enter");
    try {
      await expect(textarea).toHaveValue("", { timeout: afterEnterEmptyTimeoutMs });
      return true;
    } catch {
      return false;
    }
  }

  await prepareComposer();
  if (await clickSendWhenEnabled(sendEnabledTimeoutMs)) {
    return;
  }

  if (page.isClosed()) {
    throw new Error("sendChatMessage: page closed before composer recovery.");
  }
  await textarea.clear();
  await textarea.fill(message);
  if (await clickSendWhenEnabled(recoverySendTimeoutMs)) {
    return;
  }

  if (await submitViaEnterAndConfirm()) {
    return;
  }

  const expandConvList = page.getByRole("button", {
    name: /Show conversation list|Mostrar lista de conversaciones/i,
  });
  try {
    await expect(newConversationButton).toBeVisible({ timeout: 3_000 });
  } catch {
    await expandConvList.click({ timeout: 5_000 });
    await expect(newConversationButton).toBeVisible({ timeout: 8_000 });
  }
  await newConversationButton.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  const presetSelect = dialog.getByLabel(/initial preset/i);
  await expect(presetSelect).toBeVisible({ timeout: 15_000 });
  const compatibleChunkPreset = presetSelect.locator("option").filter({ hasText: /P3 .*chunk-level dense retrieval/i }).first();
  if ((await compatibleChunkPreset.count()) > 0 && !(await compatibleChunkPreset.isDisabled())) {
    const value = await compatibleChunkPreset.getAttribute("value");
    if (value) {
      await presetSelect.selectOption(value);
    }
  }
  await dialog.getByRole("button", { name: /^(create conversation|create|crear conversación|crear)$/i }).click();
  await expect(dialog).not.toBeVisible({ timeout: 20_000 });
  await expect(page).toHaveURL(/[?&]conversationId=[a-f0-9-]{36}/i, { timeout: 20_000 });
  const conversationId = new URL(page.url()).searchParams.get("conversationId");
  if (conversationId) {
    await expect(page.getByTestId(`conversation-item-${conversationId}`)).toBeVisible({ timeout: 20_000 });
  }
  await prepareComposer();
  if (await clickSendWhenEnabled(sendEnabledTimeoutMs)) {
    return;
  }

  throw new Error(
    "sendChatMessage: could not send — Send stayed disabled after refill, Enter did not clear the composer, " +
      "and retry after starting a new conversation failed.",
  );
}
