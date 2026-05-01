import type { ApiError } from "@/lib/api-client";

/**
 * Reads canonical API error `code` from JSON bodies produced by the backend (e.g. AuthTokenException).
 * Never logs or surfaces raw tokens.
 */
export function parseAuthApiErrorCode(error: unknown): string | undefined {
  if (!error || typeof error !== "object") return undefined;
  const meta = (error as ApiError).meta;
  const preview = meta?.rawBodyPreview;
  if (!preview || typeof preview !== "string") return undefined;
  try {
    const o = JSON.parse(preview) as { code?: unknown };
    return typeof o.code === "string" ? o.code : undefined;
  } catch {
    return undefined;
  }
}
