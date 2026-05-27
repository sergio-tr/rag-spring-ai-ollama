import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiFetch } from "@/lib/api-client";
import { resolveEmbeddingCampaignIndexSnapshotIds } from "./embedding-campaign-index-snapshots";

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

describe("resolveEmbeddingCampaignIndexSnapshotIds", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("returns aligned ids when active snapshot matches each model", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: "snap-a",
      indexProfile: { embeddingModelId: "mxbai-embed-large:latest" },
    });

    const out = await resolveEmbeddingCampaignIndexSnapshotIds("proj-1", [
      "mxbai-embed-large",
      "mxbai-embed-large:latest",
    ]);

    expect(out.snapshotIds).toEqual(["snap-a", "snap-a"]);
    expect(out.unresolvedModels).toEqual([]);
  });

  it("marks models unresolved when active snapshot uses a different embedding", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: "snap-a",
      indexProfile: { embeddingModelId: "nomic-embed-text" },
    });

    const out = await resolveEmbeddingCampaignIndexSnapshotIds("proj-1", [
      "mxbai-embed-large",
      "nomic-embed-text",
    ]);

    expect(out.snapshotIds).toEqual(["", "snap-a"]);
    expect(out.unresolvedModels).toEqual(["mxbai-embed-large"]);
  });
});
