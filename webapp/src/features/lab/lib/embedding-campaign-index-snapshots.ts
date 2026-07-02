import { apiFetch, apiProductPath } from "@/lib/api-client";

type ActiveSnapshotResponse = Readonly<{
  id?: string;
  indexProfile?: Record<string, unknown>;
}>;

function normalizeEmbeddingKey(modelId: string): string {
  const normalized = modelId.trim().toLowerCase();
  if (!normalized) return "";
  if (normalized.endsWith(":latest")) {
    return normalized.slice(0, -":latest".length);
  }
  return normalized;
}

function snapshotMatchesModel(snapshot: ActiveSnapshotResponse | null, modelId: string): string | null {
  const profileModel =
    typeof snapshot?.indexProfile?.embeddingModelId === "string"
      ? snapshot.indexProfile.embeddingModelId.trim()
      : "";
  if (!snapshot?.id || !profileModel) return null;
  return normalizeEmbeddingKey(profileModel) === normalizeEmbeddingKey(modelId) ? snapshot.id : null;
}

/**
 * Resolves index snapshot ids aligned with embedding model ids for a comparison campaign.
 * When the client cannot resolve every model, returns partial ids so the backend can auto-align
 * from project snapshot history (requires projectId on the benchmark request).
 */
export async function resolveEmbeddingCampaignIndexSnapshotIds(
  projectId: string,
  embeddingModelIds: string[],
): Promise<{ snapshotIds: string[]; unresolvedModels: string[] }> {
  const models = embeddingModelIds.map((m) => m.trim()).filter(Boolean);
  if (models.length === 0) {
    return { snapshotIds: [], unresolvedModels: [] };
  }

  const activeSnap = await apiFetch<ActiveSnapshotResponse | null>(
    apiProductPath(`/projects/${projectId}/knowledge/snapshots/active`),
  ).catch(() => null);

  const snapshotIds: string[] = [];
  const unresolvedModels: string[] = [];
  for (const model of models) {
    const matched = snapshotMatchesModel(activeSnap, model);
    if (matched) {
      snapshotIds.push(matched);
    } else {
      snapshotIds.push("");
      unresolvedModels.push(model);
    }
  }

  return { snapshotIds, unresolvedModels };
}
