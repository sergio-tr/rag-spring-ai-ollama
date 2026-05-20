/**
 * Browser API client for the Spring BFF.
 *
 * Env: `NEXT_PUBLIC_API_BASE_URL` (e.g. http://localhost:9000), `NEXT_PUBLIC_RAG_API_PREFIX` (falls back to **`/api/v5`** when unset).
 * Product routes: GET/POST/PATCH/DELETE `{prefix}/projects`, `{prefix}/projects/{id}/documents`,
 * GET/PUT `{prefix}/config/user`, GET/PUT/DELETE `{prefix}/config/project/{id}`, GET `{prefix}/config/schema`,
 * GET `{prefix}/presets`, POST/DELETE `{prefix}/presets/{id}`. Auth: `{prefix}/auth/*`. See `webapp/README.md` and backend OpenAPI (`/v3/api-docs`).
 */
import { getAccessToken, setAccessToken } from "@/lib/access-token";
import { createTraceparent } from "@/lib/traceparent";

const DEBUG_BODY_PREVIEW_CHARS = 500;

function normalizeProductApiPrefix(raw: string | undefined, fallback: string): string {
  const s = (raw ?? fallback).trim();
  if (!s) {
    return fallback;
  }
  let p = s.startsWith("/") ? s : `/${s}`;
  if (p.length > 1 && p.endsWith("/")) {
    p = p.slice(0, -1);
  }
  return p;
}

/** Authenticated product REST API prefix (must match Spring `rag.api.product-base-path`). */
const RAG_API_PRODUCT_PREFIX = normalizeProductApiPrefix(
  process.env.NEXT_PUBLIC_RAG_API_PREFIX,
  "/api/v5",
);

/**
 * Absolute URL path for a product API resource (prefix + segment).
 *
 * @param path - Path starting with "/" (e.g. "/projects", "/lab/status").
 */
export function apiProductPath(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${RAG_API_PRODUCT_PREFIX}${p}`;
}

export function getRagApiProductPrefix(): string {
  return RAG_API_PRODUCT_PREFIX;
}

export function authApiPath(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${RAG_API_PRODUCT_PREFIX}/auth${p}`;
}

/**
 * Full browser URL for product API paths (e.g. OAuth redirects using `<a href>`).
 * When `NEXT_PUBLIC_API_BASE_URL` is empty/whitespace, returns a same-origin path for nginx reverse-proxy.
 * When set (e.g. `http://127.0.0.1:9000`), prefixes so navigation works if the UI is opened on the webapp port only.
 */
export function resolveBrowserProductApiUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const trimmed = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").trim().replace(/\/$/, "");
  if (!trimmed) {
    return normalizedPath;
  }
  return `${trimmed}${normalizedPath}`;
}

function resolveApiUrl(path: string): string {
  if (path.startsWith("http")) {
    return path;
  }
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return resolveBrowserProductApiUrl(normalizedPath);
}

export type ApiClientOptions = RequestInit & {
  /** Skip attaching traceparent (rare) */
  skipTraceparent?: boolean;
  /** Skip credentials (public endpoints) */
  skipCredentials?: boolean;
};

const unauthorizedListeners = new Set<() => void>();

function buildAuthHeaders(args: {
  initHeaders: HeadersInit | undefined;
  skipTraceparent: boolean | undefined;
  skipCredentials: boolean | undefined;
  allowFormDataContentTypeRemoval: boolean;
  body: RequestInit["body"];
}): Headers {
  const {
    initHeaders,
    skipTraceparent,
    skipCredentials,
    allowFormDataContentTypeRemoval,
    body,
  } = args;

  const headers = new Headers(initHeaders);
  if (allowFormDataContentTypeRemoval && body instanceof FormData) {
    headers.delete("Content-Type");
  }
  if (!skipTraceparent && !headers.has("traceparent")) {
    headers.set("traceparent", createTraceparent());
  }
  if (!skipCredentials) {
    const bearer = getAccessToken();
    if (bearer && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${bearer}`);
    }
  }
  return headers;
}

/**
 * Subscribe to "final" 401 responses after a refresh attempt (session expired).
 * Used to redirect to login; login/register calls use `skipCredentials` and do not trigger this.
 */
export function onApiUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

function notifyUnauthorized(): void {
  unauthorizedListeners.forEach((fn) => {
    try {
      fn();
    } catch {
      /* ignore */
    }
  });
}

let refreshPromise: Promise<boolean> | null = null;

/**
 * Single-flight refresh via same-origin BFF route (httpOnly refresh cookie). Exported for SSE and schedulers.
 */
export async function tryRefreshAccessToken(): Promise<boolean> {
  return tryRefreshOnce();
}

async function tryRefreshOnce(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const res = await fetch(authApiPath("/refresh"), {
        method: "POST",
        credentials: "same-origin",
      });
      if (!res.ok) {
        return false;
      }
      const body = (await res.json().catch(() => null)) as {
        accessToken?: string;
      } | null;
      if (body?.accessToken) {
        setAccessToken(body.accessToken);
        void import("@/lib/auth-access-scheduler").then((mod) =>
          mod.scheduleAccessTokenRefreshFromJwt(body.accessToken),
        );
      }
      return true;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

export type ApiErrorKind = "http" | "network" | "timeout" | "abort" | "unknown";

export type ApiErrorMeta = {
  kind: ApiErrorKind;
  safeMessage?: string;
  /** Parsed JSON body when the response was JSON (including error responses). */
  parsedJson?: unknown | null;
  details?: Record<string, unknown>;
  contentType?: string | null;
  /** Bounded slice of raw body for logs/debug only — never render unbounded HTML to users. */
  rawBodyPreview?: string;
  diagnostics?: {
    traceparent?: string | null;
    requestId?: string | null;
    url?: string;
    method?: string;
  };
  url?: string;
  method?: string;
};

function looksLikeHtml(body: string): boolean {
  const t = body.trim().toLowerCase();
  return (
    t.startsWith("<!doctype html") ||
    t.startsWith("<html") ||
    (t.includes("<html") && t.includes("</")) ||
    (t.includes("<body") && t.includes("</body"))
  );
}

/** ASCII whitespace roughly matching `\s` for stack-frame detection (linear-time; avoids regex ReDoS). */
function isAsciiLikeWhitespace(c: number): boolean {
  return c === 9 || c === 10 || c === 11 || c === 12 || c === 13 || c === 32;
}

/**
 * Backend job errorMessage values must never be shown raw when they look like stacks.
 *
 * Equivalent intent to matching `\n` + optional ASCII-like whitespace + `at` + whitespace, twice or more,
 * without super-linear regex backtracking (Sonar S5852).
 */
function looksLikeStackTrace(s: string): boolean {
  let count = 0;
  let searchFrom = 0;
  while (searchFrom < s.length) {
    const nl = s.indexOf("\n", searchFrom);
    if (nl === -1) {
      break;
    }
    let j = nl + 1;
    while (j < s.length && isAsciiLikeWhitespace(s.codePointAt(j) ?? -1)) {
      j++;
    }
    const at0 = s.codePointAt(j) ?? -1;
    const at1 = s.codePointAt(j + 1) ?? -1;
    const afterAt = s.codePointAt(j + 2) ?? -1;
    if (at0 === 97 && at1 === 116 && isAsciiLikeWhitespace(afterAt)) {
      count++;
      if (count >= 2) {
        return true;
      }
    }
    searchFrom = nl + 1;
  }
  return false;
}

function trimSafeMessage(s: string, max = 280): string {
  const x = s.trim();
  if (x.length <= max) return x;
  return `${x.slice(0, max)}…`;
}

function extractJsonDetail(parsed: unknown): string | null {
  if (parsed === null || parsed === undefined) return null;
  if (typeof parsed === "string") return trimSafeMessage(parsed, 400);
  if (typeof parsed !== "object") return null;
  const o = parsed as Record<string, unknown>;
  const nested = o.error as Record<string, unknown> | undefined;
  const candidates = [
    o.detail,
    o.message,
    o.title,
    nested?.detail,
    nested?.message,
  ];
  for (const c of candidates) {
    if (typeof c === "string" && c.length > 0) return trimSafeMessage(c, 400);
  }
  return null;
}

function extractValidationDetails(parsed: unknown): Record<string, unknown> | undefined {
  if (!parsed || typeof parsed !== "object") return undefined;
  const o = parsed as Record<string, unknown>;
  const fieldErrors = o.fieldErrors;
  if (fieldErrors && typeof fieldErrors === "object") {
    return { fieldErrors: fieldErrors as Record<string, unknown> };
  }
  const errors = o.errors;
  if (Array.isArray(errors)) {
    return { errors };
  }
  return undefined;
}

function buildSafeMessage(args: {
  status: number;
  contentType: string | null;
  bodyText: string;
  parsedJson: unknown | null;
  kind: "json" | "html" | "text" | "unknown";
}): string {
  const { status, contentType, bodyText, parsedJson, kind } = args;

  if (status === 401) return "Session expired or not signed in. Please sign in and retry.";
  if (status === 403) return "Insufficient permissions for this action. Check your account role and retry.";
  if (status === 404) return "Not found.";

  const fromJson = parsedJson === null ? null : extractJsonDetail(parsedJson);
  if (fromJson !== null && fromJson !== "") {
    return fromJson;
  }

  if (kind === "html" || contentType?.includes("text/html") || looksLikeHtml(bodyText)) {
    if (status === 502 || status === 503 || status === 504) {
      return "Gateway error: upstream service unavailable. Check backend/reverse-proxy.";
    }
    return "Server returned an HTML error page. Check backend/reverse-proxy.";
  }

  if (status === 502 || status === 503 || status === 504) {
    return "Service unavailable (gateway or upstream). Please try again.";
  }

  if (status >= 500) {
    return "Server error. Please try again.";
  }

  if (bodyText && !looksLikeHtml(bodyText) && bodyText.length <= 600) {
    return trimSafeMessage(bodyText, 400);
  }

  return `Request failed (${status}).`;
}

function previewBody(body: string): string {
  if (!body) return "";
  return body.length > DEBUG_BODY_PREVIEW_CHARS
    ? `${body.slice(0, DEBUG_BODY_PREVIEW_CHARS)}…`
    : body;
}

function classifyErrorBody(contentType: string | null, bodyText: string): {
  bodyKind: "json" | "html" | "text" | "unknown";
  parsedJson: unknown | null;
} {
  if (contentType?.includes("application/json")) {
    try {
      const parsedJson: unknown = JSON.parse(bodyText);
      return { bodyKind: "json", parsedJson };
    } catch {
      return { bodyKind: "text", parsedJson: null };
    }
  }
  const trimmed = bodyText.trim();
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    try {
      const parsedJson: unknown = JSON.parse(bodyText);
      return { bodyKind: "json", parsedJson };
    } catch {
      /* fall through */
    }
  }
  if (contentType?.includes("text/html") || looksLikeHtml(bodyText)) {
    return { bodyKind: "html", parsedJson: null };
  }
  return { bodyKind: bodyText ? "text" : "unknown", parsedJson: null };
}

export function createHttpApiError(args: {
  status: number;
  bodyText: string;
  headers: Headers;
  requestUrl: string;
  method: string;
}): ApiError {
  const ct = args.headers.get("content-type");
  const { bodyKind, parsedJson } = classifyErrorBody(ct, args.bodyText);
  const safeMessage = buildSafeMessage({
    status: args.status,
    contentType: ct,
    bodyText: args.bodyText,
    parsedJson,
    kind: bodyKind,
  });
  const details = extractValidationDetails(parsedJson);
  const meta: ApiErrorMeta = {
    kind: "http",
    safeMessage,
    parsedJson,
    details,
    contentType: ct,
    rawBodyPreview: previewBody(args.bodyText),
    diagnostics: {
      traceparent: args.headers.get("traceparent"),
      requestId: args.headers.get("x-request-id"),
      url: args.requestUrl,
      method: args.method,
    },
    url: args.requestUrl,
    method: args.method,
  };
  return new ApiError(args.status, safeMessage, meta);
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly meta?: ApiErrorMeta,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Sanitize plain-text hints from async jobs or similar (no HTML pages, no stack dumps).
 */
export function sanitizePlainErrorTextForUi(raw: string | undefined | null, maxLen = 280): string {
  const t = (raw ?? "").trim();
  if (!t) return "";
  if (looksLikeHtml(t)) return "";
  if (looksLikeStackTrace(t)) return "";
  return trimSafeMessage(t, maxLen);
}

/**
 * User-safe message for UI (never raw HTML pages).
 */
export function getSafeApiErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return "Unexpected error. Please try again.";
  return String(error);
}

function throwIfNetworkFailure(e: unknown, url: string, method: string): never {
  const isAbort = e instanceof DOMException && e.name === "AbortError";
  throw new ApiError(0, isAbort ? "Request was cancelled." : "Network error. Check connection and server availability.", {
    kind: isAbort ? "abort" : "network",
    url,
    method,
  });
}

async function fetchProductOnce(
  doRequest: () => Promise<Response>,
  url: string,
  method: string,
): Promise<Response> {
  try {
    return await doRequest();
  } catch (e) {
    throwIfNetworkFailure(e, url, method);
  }
}

async function preferFreshResponseAfter401(
  res: Response,
  doRequest: () => Promise<Response>,
  skipCredentials: boolean | undefined,
  url: string,
  method: string,
): Promise<Response> {
  if (res.status !== 401 || skipCredentials) {
    return res;
  }
  const refreshed = await tryRefreshOnce();
  if (!refreshed) {
    return res;
  }
  return fetchProductOnce(doRequest, url, method);
}

/**
 * Typed fetch to the Spring (or BFF) API with trace context and session cookies.
 */
export async function apiFetch<T = unknown>(
  path: string,
  options: ApiClientOptions = {},
): Promise<T> {
  const {
    skipTraceparent,
    skipCredentials,
    headers: initHeaders,
    ...rest
  } = options;

  const url = resolveApiUrl(path);
  const method = (rest.method ?? "GET").toUpperCase();

  const doRequest = createDoRequest(url, {
    rest,
    initHeaders,
    skipTraceparent,
    skipCredentials,
    allowFormDataContentTypeRemoval: true,
  });

  let res = await fetchProductOnce(doRequest, url, method);
  res = await preferFreshResponseAfter401(res, doRequest, skipCredentials, url, method);

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    if (res.status === 401 && !skipCredentials) {
      notifyUnauthorized();
    }
    throw createHttpApiError({
      status: res.status,
      bodyText: text || res.statusText,
      headers: res.headers,
      requestUrl: url,
      method,
    });
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const ct = res.headers.get("content-type");
  if (ct?.includes("application/json")) {
    return (await res.json()) as T;
  }

  return (await res.text()) as T;
}

/**
 * Authenticated GET returning a binary body (e.g. account export ZIP).
 */
export async function apiDownloadBlob(path: string, options: ApiClientOptions = {}): Promise<Blob> {
  const {
    skipTraceparent,
    skipCredentials,
    headers: initHeaders,
    ...rest
  } = options;

  const url = resolveApiUrl(path);
  const method = "GET";

  const doRequest = createDoRequest(url, {
    rest: { ...rest, method: "GET" },
    initHeaders,
    skipTraceparent,
    skipCredentials,
    allowFormDataContentTypeRemoval: false,
  });

  let res = await fetchProductOnce(doRequest, url, method);
  res = await preferFreshResponseAfter401(res, doRequest, skipCredentials, url, method);

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    if (res.status === 401 && !skipCredentials) {
      notifyUnauthorized();
    }
    throw createHttpApiError({
      status: res.status,
      bodyText: text || res.statusText,
      headers: res.headers,
      requestUrl: url,
      method,
    });
  }

  return res.blob();
}

function createDoRequest(
  url: string,
  args: {
    rest: RequestInit;
    initHeaders: HeadersInit | undefined;
    skipTraceparent: boolean | undefined;
    skipCredentials: boolean | undefined;
    allowFormDataContentTypeRemoval: boolean;
  },
) {
  const {
    rest,
    initHeaders,
    skipTraceparent,
    skipCredentials,
    allowFormDataContentTypeRemoval,
  } = args;

  return () =>
    fetch(url, {
      ...rest,
      credentials: skipCredentials ? "omit" : "include",
      headers: buildAuthHeaders({
        initHeaders,
        skipTraceparent,
        skipCredentials,
        allowFormDataContentTypeRemoval,
        body: rest.body,
      }),
    });
}

/** Trimmed backend origin, or empty when using same-origin `/api/v5/*` (reverse-proxy). Read live from env. */
export function getApiBaseUrl(): string {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").trim().replace(/\/$/, "");
}
