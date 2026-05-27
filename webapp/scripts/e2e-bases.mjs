/**
 * Shared E2E base URL resolution for stack preflight and Playwright fixtures.
 *
 * Contract (demo reverse-proxy):
 * - E2E_PUBLIC_BASE_URL / PLAYWRIGHT_BASE_URL → https://127.0.0.1:8444 (UI + /actuator/* at origin)
 * - API_BASE_URL → same origin when using proxy (product API at /api/v5/*)
 * - E2E_BACKEND_HEALTH_URL → origin only (never …/api/v5); actuator is not under the product prefix
 */

const REVERSE_PROXY_PORTS = new Set(["80", "443", "8080", "8443", "8444"]);

/** Apply demo-proxy defaults when CI has not pinned URLs (official stack entrypoint). */
export function applyDemoProxyEnvDefaults() {
  if (process.env.CI === "true" || process.env.CI === "1") {
    return;
  }
  if (!process.env.PLAYWRIGHT_BASE_URL && !process.env.E2E_PUBLIC_BASE_URL) {
    process.env.PLAYWRIGHT_BASE_URL = "https://127.0.0.1:8444";
  }
  if (process.env.PLAYWRIGHT_SKIP_WEBSERVER == null) {
    process.env.PLAYWRIGHT_SKIP_WEBSERVER = "1";
  }
  if (process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS == null) {
    process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS = "1";
  }
  if (process.env.NODE_TLS_REJECT_UNAUTHORIZED == null) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  }
}

export function isReverseProxyOrigin(urlString) {
  try {
    const u = new URL(urlString);
    const port = u.port || (u.protocol === "https:" ? "443" : "80");
    return REVERSE_PROXY_PORTS.has(port);
  } catch {
    return false;
  }
}

/** Actuator is mounted at servlet root; strip mistaken /api/v5 suffix on health base. */
export function normalizeHealthBase(url) {
  return url.replace(/\/$/, "").replace(/\/api\/v5\/?$/i, "");
}

export function resolveE2eBases() {
  applyDemoProxyEnvDefaults();

  const publicBase = (
    process.env.E2E_PUBLIC_BASE_URL ??
    process.env.PLAYWRIGHT_BASE_URL ??
    "http://127.0.0.1:3000"
  ).replace(/\/$/, "");

  let apiBase = process.env.API_BASE_URL ?? process.env.INTEGRATION_BACKEND_URL;
  if (!apiBase) {
    apiBase = isReverseProxyOrigin(publicBase) ? publicBase : "http://127.0.0.1:9000";
  }
  apiBase = apiBase.replace(/\/$/, "");

  const healthBase = normalizeHealthBase(process.env.E2E_BACKEND_HEALTH_URL ?? publicBase);

  const webBase = publicBase;
  const webHealthBase = (process.env.E2E_WEB_HEALTH_URL ?? webBase).replace(/\/$/, "");

  return { publicBase, apiBase, healthBase, webBase, webHealthBase };
}

export function actuatorHealthUrl(healthBase, suffix = "") {
  const path = suffix.startsWith("/") ? suffix : suffix ? `/${suffix}` : "";
  return `${normalizeHealthBase(healthBase)}/actuator/health${path}`;
}
