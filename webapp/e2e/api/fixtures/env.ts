/**
 * Backend base URL and API prefixes (aligned with Spring `rag.api.*` and integration pytest env).
 */

export function apiBaseUrl(): string {
  const raw =
    process.env.API_BASE_URL ?? process.env.INTEGRATION_BACKEND_URL ?? "http://127.0.0.1:9000";
  return raw.replace(/\/$/, "");
}

export function productBasePath(): string {
  const raw =
    process.env.RAG_API_PRODUCT_BASE_PATH ?? process.env.INTEGRATION_RAG_PRODUCT_BASE_PATH ?? "/api/v5";
  const p = raw.replace(/\/$/, "");
  return p || "/api/v5";
}

/** Full URL for product API path (e.g. `/projects` → `http://host:9000/api/v5/projects`). */
export function productUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${apiBaseUrl()}${productBasePath()}${p}`;
}

export function integrationCredentials(): { email: string; password: string } {
  return {
    email: process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test",
    password: process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev",
  };
}
