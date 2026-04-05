/**
 * Browser API client for the Spring BFF.
 *
 * Env: `NEXT_PUBLIC_API_BASE_URL` (e.g. http://localhost:9000), `NEXT_PUBLIC_RAG_API_PREFIX` (default /api/v5).
 * Product routes: GET/POST/PATCH/DELETE `{prefix}/projects`, `{prefix}/projects/{id}/documents`,
 * GET/PUT `{prefix}/config/user`, GET/PUT/DELETE `{prefix}/config/project/{id}`, GET `{prefix}/config/schema`,
 * GET `{prefix}/presets`, POST/DELETE `{prefix}/presets/{id}`. Auth: `/api/auth/*`. See `webapp/README.md` and backend OpenAPI (`/v3/api-docs`).
 */
import { getAccessToken, setAccessToken } from "@/lib/access-token";
import { createTraceparent } from "@/lib/traceparent";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:9000";

function resolveApiUrl(path: string): string {
  if (path.startsWith("http")) {
    return path;
  }
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE}${normalizedPath}`;
}

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

export type ApiClientOptions = RequestInit & {
  /** Skip attaching traceparent (rare) */
  skipTraceparent?: boolean;
  /** Skip credentials (public endpoints) */
  skipCredentials?: boolean;
};

const unauthorizedListeners = new Set<() => void>();

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

async function tryRefreshOnce(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const res = await fetch("/api/auth/refresh", {
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

  const buildHeaders = () => {
    const headers = new Headers(initHeaders);
    if (rest.body instanceof FormData) {
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
  };

  const doRequest = () =>
    fetch(url, {
      ...rest,
      credentials: skipCredentials ? "omit" : "include",
      headers: buildHeaders(),
    });

  let res = await doRequest();

  if (res.status === 401 && !skipCredentials) {
    const refreshed = await tryRefreshOnce();
    if (refreshed) {
      res = await doRequest();
    }
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    if (res.status === 401 && !skipCredentials) {
      notifyUnauthorized();
    }
    throw new ApiError(res.status, text || res.statusText);
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

  const buildHeaders = () => {
    const headers = new Headers(initHeaders);
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
  };

  const doRequest = () =>
    fetch(url, {
      ...rest,
      method: "GET",
      credentials: skipCredentials ? "omit" : "include",
      headers: buildHeaders(),
    });

  let res = await doRequest();

  if (res.status === 401 && !skipCredentials) {
    const refreshed = await tryRefreshOnce();
    if (refreshed) {
      res = await doRequest();
    }
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    if (res.status === 401 && !skipCredentials) {
      notifyUnauthorized();
    }
    throw new ApiError(res.status, text || res.statusText);
  }

  return res.blob();
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function getApiBaseUrl(): string {
  return API_BASE;
}
