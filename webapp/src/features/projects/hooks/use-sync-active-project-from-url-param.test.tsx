import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ApiError, apiFetch } from "@/lib/api-client";
import { useSyncActiveProjectFromUrlParam } from "./use-sync-active-project-from-url-param";
import { useAppStore } from "@/store/app.store";

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

const { replaceMock, mutateAsyncMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  mutateAsyncMock: vi.fn(async () => {}),
}));

vi.mock("@/navigation", () => ({
  useRouter: () => ({
    replace: replaceMock,
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useActivateProject: () => ({
    mutateAsync: mutateAsyncMock,
    mutate: vi.fn(),
    isPending: false,
  }),
}));

describe("useSyncActiveProjectFromUrlParam", () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    qc.clear();
    useAppStore.setState({ activeProject: null });
    replaceMock.mockReset();
    mutateAsyncMock.mockReset();
    vi.mocked(apiFetch).mockReset();
  });

  it("does nothing when URL project id matches active project", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "One" } });
    renderHook(() => useSyncActiveProjectFromUrlParam("p1", "/chat"), { wrapper });
    expect(mutateAsyncMock).not.toHaveBeenCalled();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("activates from cached project list when URL project differs (chat fallback)", async () => {
    qc.setQueryData(["projects", 0, 64], {
      items: [
        {
          id: "p2",
          name: "Two",
          docCount: 0,
          convCount: 0,
          updatedAt: "",
          colorHex: null,
          iconKey: null,
          description: null,
        },
      ],
      total: 1,
    });

    renderHook(() => useSyncActiveProjectFromUrlParam("p2", "/chat"), { wrapper });

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith(
        expect.objectContaining({ id: "p2", name: "Two" }),
      );
    });
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("replaces documents route on invalid project id", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(404, "Not found"));

    renderHook(() => useSyncActiveProjectFromUrlParam("missing", "/documents"), { wrapper });

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/documents");
    });
    expect(mutateAsyncMock).not.toHaveBeenCalled();
  });
});
