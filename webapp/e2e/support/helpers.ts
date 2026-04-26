import { expect, type Page } from "@playwright/test";
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
  await page.goto("/en/login");
  await page.getByLabel(/email|correo/i).fill(seedEmail());
  await page.getByLabel(/^password$/i).fill(seedPassword());
  await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
  await expect(page).toHaveURL(/\/en\/projects/, { timeout: 30_000 });
  await expect(
    page.getByRole("heading", { name: /^projects$/i }),
  ).toBeVisible({ timeout: 30_000 });
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
  await page.getByText(projectName, { exact: true }).waitFor({ state: "visible" });
  const projectCard = page.locator('[data-slot="card"]').filter({ hasText: projectName }).first();
  await expect(projectCard.getByRole("button", { name: /^(active|activo)$/i })).toBeVisible({
    timeout: 20_000,
  });
}

/** ADMIN user seeded when Spring profile {@code e2e} is active (see E2eAdminUserSeeder). */
export async function loginAsE2eAdmin(page: Page): Promise<void> {
  await page.goto("/en/login");
  await page.getByLabel(/email|correo/i).fill(adminEmail());
  await page.getByLabel(/^password$/i).fill(adminPassword());
  await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
  await expect(page).toHaveURL(/\/en\/projects/, { timeout: 30_000 });
}
