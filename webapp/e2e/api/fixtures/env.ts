/**
 * Backend base URL and API prefixes (aligned with Spring `rag.api.*` and scripts/e2e-bases.mjs).
 */

const REVERSE_PROXY_PORTS = new Set(["80", "443", "8080", "8443", "8444"]);
const DEMO_PROXY_ORIGIN = "https://127.0.0.1:8444";

function isReverseProxyOrigin(urlString: string): boolean {
  try {
    const u = new URL(urlString);
    const port = u.port || (u.protocol === "https:" ? "443" : "80");
    return REVERSE_PROXY_PORTS.has(port);
  } catch {
    return false;
  }
}

function applyLocalDemoDefaults(): void {
  if (process.env.CI === "true" || process.env.CI === "1") {
    return;
  }
  if (!process.env.PLAYWRIGHT_BASE_URL && !process.env.E2E_PUBLIC_BASE_URL) {
    process.env.PLAYWRIGHT_BASE_URL = DEMO_PROXY_ORIGIN;
  }
  if (
    !process.env.E2E_PRODUCT_URL &&
    !process.env.API_BASE_URL &&
    !process.env.INTEGRATION_BACKEND_URL
  ) {
    const pub =
      process.env.E2E_PUBLIC_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL ?? DEMO_PROXY_ORIGIN;
    if (isReverseProxyOrigin(pub)) {
      process.env.E2E_PRODUCT_URL = pub.replace(/\/$/, "");
    }
  }
}

/** Public UI origin (reverse-proxy entrypoint for demo E2E). */
export function publicBaseUrl(): string {
  applyLocalDemoDefaults();
  const raw =
    process.env.E2E_PUBLIC_BASE_URL ??
    process.env.PLAYWRIGHT_BASE_URL ??
    process.env.E2E_PRODUCT_URL ??
    DEMO_PROXY_ORIGIN;
  return raw.replace(/\/$/, "");
}

/** Product API origin (scheme + host + port). Official: E2E_PRODUCT_URL. */
export function apiBaseUrl(): string {
  applyLocalDemoDefaults();
  const explicit =
    process.env.E2E_PRODUCT_URL ??
    process.env.API_BASE_URL ??
    process.env.INTEGRATION_BACKEND_URL;
  if (explicit) {
    return explicit.replace(/\/$/, "");
  }
  const pub = publicBaseUrl();
  return isReverseProxyOrigin(pub) ? pub : "http://127.0.0.1:9000";
}

/** Actuator health at servlet root (proxied as {origin}/actuator/*, not under /api/v5). */
export function actuatorHealthUrl(suffix = ""): string {
  const base = (process.env.E2E_BACKEND_HEALTH_URL ?? apiBaseUrl())
    .replace(/\/$/, "")
    .replace(/\/api\/v5\/?$/i, "");
  const path = suffix.startsWith("/") ? suffix : suffix ? `/${suffix}` : "";
  return `${base}/actuator/health${path}`;
}

export function productBasePath(): string {
  const raw =
    process.env.RAG_API_PRODUCT_BASE_PATH ??
    process.env.INTEGRATION_RAG_PRODUCT_BASE_PATH ??
    process.env.NEXT_PUBLIC_RAG_API_PREFIX ??
    "/api/v5";
  const p = raw.replace(/\/$/, "");
  return p || "/api/v5";
}

/** Full URL for product API path (e.g. `/projects` → `{E2E_PRODUCT_URL}/api/v5/projects`). */
export function productUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${apiBaseUrl()}${productBasePath()}${p}`;
}

export function integrationCredentials(): { email: string; password: string } {
  return {
    email: process.env.INTEGRATION_LOGIN_EMAIL ?? process.env.E2E_SEED_EMAIL ?? "dev@local.test",
    password: process.env.INTEGRATION_LOGIN_PASSWORD ?? process.env.E2E_SEED_PASSWORD ?? "dev",
  };
}
