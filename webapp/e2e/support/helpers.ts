import { expect, type Locator, type Page } from "@playwright/test";
import { adminEmail, adminPassword, seedEmail, seedPassword } from "../fixtures/users";

const apiBase = () =>
  (
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    process.env.API_BASE_URL ??
    process.env.INTEGRATION_BACKEND_URL ??
    "http://127.0.0.1:9000"
  ).replace(/\/$/, "");
const productPrefix = () =>
  (process.env.NEXT_PUBLIC_RAG_API_PREFIX ?? "/api/v5").replace(/\/$/, "");
const loginTimeoutMs = Number.parseInt(process.env.E2E_LOGIN_TIMEOUT_MS ?? "12000", 10);

/** Full URL for product API path (e.g. `/projects`). */
export function productApiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${apiBase()}${productPrefix()}${p}`;
}

export async function authHeadersFromPage(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem("rag_access_token"));
  const h: Record<string, string> = { Accept: "application/json" };
  if (token) {
    h.Authorization = `Bearer ${token}`;
  }
  return h;
}

/** Logs in with Flyway seed credentials and waits for the projects page. */
export async function loginAsSeedUser(page: Page): Promise<void> {
  await page.goto("/en/login", { waitUntil: "domcontentloaded" });
  const emailInput = page.getByLabel(/email|correo/i);
  const passwordInput = page.getByLabel(/^password$/i);
  await expect(emailInput).toBeVisible({ timeout: loginTimeoutMs });
  await expect(emailInput).toBeEnabled({ timeout: loginTimeoutMs });
  await expect(passwordInput).toBeVisible({ timeout: loginTimeoutMs });
  await expect(passwordInput).toBeEnabled({ timeout: loginTimeoutMs });
  await emailInput.fill(seedEmail());
  await passwordInput.fill(seedPassword());
  await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
  await expect(page).toHaveURL(/\/en\/projects/, { timeout: loginTimeoutMs });
  await expect(
    page.getByRole("heading", { name: /^projects$/i }),
  ).toBeVisible({ timeout: loginTimeoutMs });
}

/**
 * Creates a project via dialog; backend activate + client store mark it active.
 * Waits until the new card shows the Active / Activo state (no extra click).
 */
export async function createAndActivateProject(page: Page, projectName: string): Promise<void> {
  await page.getByRole("button", { name: /new project|nuevo proyecto/i }).first().click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.locator("#proj-name").fill(projectName);
  await page.getByRole("button", { name: /^(create|crear)$/i }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible({ timeout: 20_000 });
  // Project name can appear both in sidebar and in the card title; pick the clickable card entry.
  await page.getByRole("button", { name: projectName, exact: true }).first().waitFor({ state: "visible" });
  const projectCard = page.locator('[data-slot="card"]').filter({ hasText: projectName }).first();
  // The grid uses "Open/Abrir" to activate the project, then the button becomes "Active/Activo".
  await projectCard.getByRole("button", { name: /^(open|abrir|activate|activar)$/i }).click();
  await expect(projectCard.getByRole("button", { name: /^(active|activo)$/i })).toBeVisible({ timeout: 20_000 });
}

/** ADMIN user seeded when Spring profile {@code e2e} is active (see E2eAdminUserSeeder). */
export async function loginAsE2eAdmin(page: Page): Promise<void> {
  await page.goto("/en/login", { waitUntil: "domcontentloaded" });
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

function chatComposerLocators(page: Page): {
  textarea: Locator;
  sendButton: Locator;
  newConversationButton: Locator;
} {
  return {
    textarea: page.getByPlaceholder(/message|mensaje/i),
    sendButton: page.getByRole("button", { name: /^send$|^enviar$/i }),
    newConversationButton: page
      .getByRole("main")
      .getByRole("button", { name: /new conversation|nueva conversación/i }),
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

  await textarea.clear();
  await textarea.fill(message);
  if (await clickSendWhenEnabled(recoverySendTimeoutMs)) {
    return;
  }

  if (await submitViaEnterAndConfirm()) {
    return;
  }

  await newConversationButton.click();
  await prepareComposer();
  if (await clickSendWhenEnabled(sendEnabledTimeoutMs)) {
    return;
  }

  throw new Error(
    "sendChatMessage: could not send — Send stayed disabled after refill, Enter did not clear the composer, " +
      "and retry after starting a new conversation failed.",
  );
}
