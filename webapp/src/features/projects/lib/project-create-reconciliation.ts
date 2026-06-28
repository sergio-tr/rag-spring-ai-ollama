import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import type { CreateProjectBody, ProjectListResponse, ProjectSummary } from "@/types/api";

export type CreateProjectOutcome = {
  project: ProjectSummary;
  /** Activate failed after POST succeeded (non-fatal except session handling in UI). */
  activateFailed?: boolean;
  /** Project was matched from list after POST error (timeout / duplicate / gateway). */
  reconciledFromList?: boolean;
};

/** POST failed in a way where the project may still exist server-side. */
export function isReconcilablePostFailure(err: unknown): boolean {
  if (!(err instanceof ApiError)) {
    return true;
  }
  const kind = err.meta?.kind;
  if (kind === "network" || kind === "timeout" || kind === "abort") {
    return true;
  }
  if (err.status === 409 || err.status === 408 || err.status === 502 || err.status === 503 || err.status === 504) {
    return true;
  }
  return false;
}

export async function listProjectsForReconcile(size = 100): Promise<ProjectSummary[]> {
  const search = new URLSearchParams({ page: "0", size: String(size) });
  const res = await apiFetch<ProjectListResponse>(apiProductPath(`/projects?${search.toString()}`));
  return res.items ?? [];
}

export function findProjectByName(items: ProjectSummary[], name: string): ProjectSummary | null {
  const target = name.trim();
  if (!target) {
    return null;
  }
  return items.find((p) => (p.name ?? "").trim() === target) ?? null;
}

/**
 * After an ambiguous POST failure, try to find a newly created project by name.
 */
export async function reconcileProjectAfterPostFailure(
  body: CreateProjectBody,
  postErr: unknown,
): Promise<ProjectSummary | null> {
  if (!isReconcilablePostFailure(postErr)) {
    return null;
  }
  try {
    const items = await listProjectsForReconcile();
    return findProjectByName(items, body.name);
  } catch {
    return null;
  }
}
