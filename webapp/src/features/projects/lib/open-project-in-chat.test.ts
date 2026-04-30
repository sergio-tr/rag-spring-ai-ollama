import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api-client";
import { fetchLatestConversationId } from "./open-project-in-chat";

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

describe("fetchLatestConversationId", () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.mocked(apiFetch).mockReset();
  });

  it("returns the most recently updated conversation id when list is non-empty", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([
      { id: "old", title: "a", updatedAt: "2020-01-01T00:00:00Z", presetId: null },
      { id: "new", title: "b", updatedAt: "2025-01-01T00:00:00Z", presetId: null },
    ]);

    const id = await fetchLatestConversationId(qc, "pid");
    expect(id).toBe("new");
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it("returns null when no conversations exist", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);

    const id = await fetchLatestConversationId(qc, "pid2");
    expect(id).toBeNull();
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });
});
