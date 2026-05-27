import { expect, type Locator, type Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import {
  conversationIdFromChatUrl,
  isProjectConversationCreateRequest,
} from "../../src/features/chat/lib/new-conversation-request";
import { apiBaseUrl, productBasePath } from "../api/fixtures/env";
import { adminEmail, adminPassword, seedEmail, seedPassword } from "../fixtures/users";

const E2E_PREFLIGHT_EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "../.cursor/evidence/wave-3-current/preflight-flake",
);

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

const loginTimeoutMs = Number.parseInt(process.env.E2E_LOGIN_TIMEOUT_MS ?? "12000", 10);

type LoginResponse = {
  accessToken: string;
  refreshToken?: string;
};

/** Full URL for product API path (e.g. `/projects`). */
export function productApiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${apiBaseUrl()}${productBasePath()}${p}`;
}

function directProductApiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  const base = (
    process.env.INTEGRATION_BACKEND_URL ??
    process.env.API_BASE_URL ??
    process.env.PLAYWRIGHT_BASE_URL ??
    "http://127.0.0.1:9000"
  ).replace(/\/$/, "");
  return `${base}${productBasePath()}${p}`;
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
  const headers = await authHeadersFromPage(page);
  const deadline = Date.now() + 25_000;
  let pageIdx = 0;
  let lastBodyText = "";
  while (Date.now() < deadline) {
    // Projects can exceed 100 rows in dev DB; page through deterministically.
    const res = await page.request.get(directProductApiUrl(`/projects?page=${pageIdx}&size=100`), { headers });
    lastBodyText = await res.text();
    expect(res.status(), lastBodyText).toBe(200);
    const body = JSON.parse(lastBodyText) as any;
    const rows = Array.isArray(body)
      ? body
      : Array.isArray(body?.items)
        ? body.items
        : Array.isArray(body?.content)
          ? body.content
          : [];
    const project = (rows as ProjectDto[]).find((p) => p?.name === projectName);
    if (project?.id) return project.id;

    // If this page had fewer than size results, we reached the end; restart from page 0 after a short delay.
    if (!Array.isArray(rows) || rows.length < 100) {
      pageIdx = 0;
      await page.waitForTimeout(350);
      continue;
    }
    pageIdx += 1;
  }
  throw new Error(`Project not found after creation: ${projectName}. Last response: ${lastBodyText.slice(0, 400)}`);
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
    let loginRes: any = null;
    let loginBody = "";
    for (let attempt = 0; attempt < 5; attempt += 1) {
      loginRes = await page.request.post(productApiUrl("/auth/login"), {
        data: { email: seedEmail(), password: seedPassword() },
        headers: { "Content-Type": "application/json" },
      });
      loginBody = await loginRes.text();
      if (loginRes.ok()) break;
      // Reverse-proxy can briefly return 502 during stack restarts; retry with a short backoff.
      if ([502, 503, 504].includes(loginRes.status())) {
        await page.waitForTimeout(400 + attempt * 350);
        continue;
      }
      break;
    }
    expect(loginRes.ok(), `seed API login failed: ${loginRes.status()} ${loginBody}`).toBeTruthy();
    const tokens = JSON.parse(loginBody) as LoginResponse;
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

/** Surfaces dialog role=alert errors when Create does not close the modal. */
async function assertProjectCreateDialogClosedOrSurfaceError(dialog: Locator): Promise<void> {
  const closed = await dialog
    .waitFor({ state: "hidden", timeout: 30_000 })
    .then(() => true)
    .catch(() => false);
  if (closed) {
    return;
  }
  const alerts = dialog.getByRole("alert");
  const alertCount = await alerts.count();
  const messages: string[] = [];
  for (let i = 0; i < alertCount; i += 1) {
    const text = (await alerts.nth(i).textContent())?.trim();
    if (text) {
      messages.push(text);
    }
  }
  throw new Error(
    messages.length > 0
      ? `Project create dialog still open; role=alert: ${messages.join(" | ")}`
      : "Project create dialog still open after Create (no role=alert text found).",
  );
}

/**
 * Creates a project via dialog; backend activate + client store mark it active.
 * Waits until the new card shows the Active / Activo state (no extra click).
 *
 * Uses form defaults (empty embedding → backend canonical tag; metadata index off).
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
  await dialog.getByRole("button", { name: /^(create|crear)$/i }).click();
  await assertProjectCreateDialogClosedOrSurfaceError(dialog);
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

export type CreateNewChatConversationOptions = {
  /** When true (default), skip wizard if URL already has a usable composer. */
  allowExisting?: boolean;
  /** Explicit project scope; required when the URL has no `?projectId=`. */
  projectId?: string;
};

/** Opens Chat scoped to a project (required for createNewChatConversation and send flows). */
export async function openChatForProject(page: Page, projectId: string): Promise<void> {
  await page.goto(`/en/chat?projectId=${projectId}`, {
    waitUntil: "domcontentloaded",
    timeout: 15_000,
  });
  await expect(page.getByTestId("chat-page")).toBeVisible({ timeout: 15_000 });
  await expect(page).toHaveURL(new RegExp(`[?&]projectId=${projectId}`), { timeout: 15_000 });
}

async function ensureChatConversationSidebarExpanded(page: Page): Promise<void> {
  const sidebar = page.getByTestId("chat-conversation-sidebar");
  if (await sidebar.isVisible().catch(() => false)) {
    return;
  }
  const expand = page.getByRole("button", {
    name: /show conversation list|mostrar lista de conversaciones|expand sidebar/i,
  });
  await expect(expand).toBeVisible({ timeout: 10_000 });
  await expand.click();
  await expect(sidebar).toBeVisible({ timeout: 10_000 });
}

function resolveChatProjectId(page: Page, explicit?: string): string {
  if (explicit?.trim()) {
    return explicit.trim();
  }
  return activeProjectIdFromUrl(page);
}

async function captureNewConversationFailure(page: Page, reason: string): Promise<string> {
  fs.mkdirSync(E2E_PREFLIGHT_EVIDENCE_DIR, { recursive: true });
  const screenshotPath = path.join(
    E2E_PREFLIGHT_EVIDENCE_DIR,
    `create-conversation-${reason.replace(/\s+/g, "-")}-${Date.now()}.png`,
  );
  await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => undefined);
  return screenshotPath;
}

function newConversationDialogLocator(page: Page): Locator {
  return page
    .getByTestId("chat-new-conversation-dialog")
    .or(page.getByRole("dialog", { name: /new conversation setup|configuración de nueva conversación/i }));
}

async function newConversationDialogDiagnostics(dialog: Locator): Promise<string> {
  const dialogText = ((await dialog.textContent().catch(() => "")) ?? "").replace(/\s+/g, " ").trim();
  const alertText = ((await dialog.getByRole("alert").textContent().catch(() => "")) ?? "")
    .replace(/\s+/g, " ")
    .trim();
  const pending = await dialog
    .getByTestId("chat-new-conversation-create")
    .or(dialog.getByRole("button", { name: /^(create conversation|crear conversación)$/i }))
    .isDisabled()
    .catch(() => false);
  return `pending=${pending} alert=${alertText.slice(0, 200)} dialog=${dialogText.slice(0, 300)}`;
}

/**
 * Opens the new-conversation wizard, waits for POST create, dialog close, and routed conversationId.
 * Returns the conversation id for callers that need it.
 */
export async function createNewChatConversation(
  page: Page,
  options?: CreateNewChatConversationOptions,
): Promise<string> {
  const allowExisting = options?.allowExisting !== false;
  const projectId = resolveChatProjectId(page, options?.projectId);

  if (allowExisting) {
    const existingId = conversationIdFromChatUrl(page.url());
    if (existingId) {
      const composer = page.getByTestId("chat-message-composer");
      if (await composer.isVisible().catch(() => false)) {
        return existingId;
      }
    }
  }

  await ensureChatConversationSidebarExpanded(page);
  const trigger = page.getByTestId("chat-new-conversation");
  const dialog = newConversationDialogLocator(page);
  await expect(trigger).toBeEnabled({ timeout: 20_000 });

  const createResponsePromise = page.waitForResponse(
    (res) => isProjectConversationCreateRequest(res.request().method(), res.url()),
    { timeout: 45_000 },
  );

  await trigger.click();
  await expect(dialog).toBeVisible({ timeout: 20_000 });

  const presetSelect = dialog
    .getByTestId("chat-new-conversation-preset")
    .or(dialog.locator("#new-conv-preset"));
  await expect(presetSelect).toBeVisible({ timeout: 20_000 });
  const compatibleChunkPreset = presetSelect
    .locator("option")
    .filter({ hasText: /P3 .*chunk-level dense retrieval/i })
    .first();
  if ((await compatibleChunkPreset.count()) > 0 && !(await compatibleChunkPreset.isDisabled())) {
    const value = await compatibleChunkPreset.getAttribute("value");
    if (value) {
      await presetSelect.selectOption(value);
    }
  }

  const createBtn = dialog
    .getByTestId("chat-new-conversation-create")
    .or(dialog.getByRole("button", { name: /^(create conversation|crear conversación)$/i }));
  await expect(createBtn).toBeEnabled({ timeout: 20_000 });
  await createBtn.click();

  let createResponse;
  try {
    createResponse = await createResponsePromise;
  } catch (e) {
    const screenshotPath = await captureNewConversationFailure(page, "no-post-response");
    const diag = await newConversationDialogDiagnostics(dialog);
    throw new Error(
      `createNewChatConversation: timed out waiting for POST /projects/{id}/conversations (45s). screenshot=${screenshotPath} url=${page.url()} ${diag} cause=${e instanceof Error ? e.message : String(e)}`,
    );
  }

  const status = createResponse.status();
  if (status < 200 || status >= 300) {
    const screenshotPath = await captureNewConversationFailure(page, `http-${status}`);
    const body = await createResponse.text().catch(() => "");
    const diag = await newConversationDialogDiagnostics(dialog);
    throw new Error(
      `createNewChatConversation: create API failed status=${status} body=${body.slice(0, 400)} screenshot=${screenshotPath} url=${page.url()} ${diag}`,
    );
  }

  let createdBody: ConversationDto | null = null;
  try {
    createdBody = (await createResponse.json()) as ConversationDto;
  } catch {
    createdBody = null;
  }

  try {
    await expect(dialog).toBeHidden({ timeout: 30_000 });
  } catch (e) {
    const screenshotPath = await captureNewConversationFailure(page, "dialog-stuck");
    const diag = await newConversationDialogDiagnostics(dialog);
    throw new Error(
      `createNewChatConversation: dialog remained open after successful create. screenshot=${screenshotPath} url=${page.url()} ${diag} cause=${e instanceof Error ? e.message : String(e)}`,
    );
  }

  let conversationId: string | null = null;
  await expect
    .poll(
      () => conversationIdFromChatUrl(page.url()) ?? createdBody?.id ?? null,
      { timeout: 20_000 },
    )
    .not.toBeNull();
  conversationId = conversationIdFromChatUrl(page.url()) ?? createdBody?.id ?? null;

  if (!conversationId) {
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
    const screenshotPath = await captureNewConversationFailure(page, "no-conversation-id");
    throw new Error(
      `createNewChatConversation: missing conversationId after create. screenshot=${screenshotPath} url=${page.url()}`,
    );
  }

  await expect(page.getByTestId(`conversation-item-${conversationId}`)).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 20_000 });
  return conversationId;
}

/** Preflight: reuse existing conversation when provided; otherwise create via wizard. */
export async function ensureChatConversationForPreflight(
  page: Page,
  projectId: string,
  existingConversationId?: string | null,
): Promise<string> {
  if (existingConversationId) {
    await page.goto(`/en/chat?projectId=${projectId}&conversationId=${existingConversationId}`, {
      waitUntil: "domcontentloaded",
      timeout: 15_000,
    });
    await expect(page.getByTestId("chat-page")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 20_000 });
    return existingConversationId;
  }
  await openChatForProject(page, projectId);
  return createNewChatConversation(page, { allowExisting: false, projectId });
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
