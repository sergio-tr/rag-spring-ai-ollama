import type { ReactNode } from "react";
import type { ReadonlyURLSearchParams } from "next/navigation";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { ApiError } from "@/lib/api-client";
import DocumentsPage from "./page";

vi.mock("@/features/projects/hooks/use-sync-active-project-from-documents-url", () => ({
  useSyncActiveProjectFromDocumentsUrl: () => {},
}));

vi.mock("next/navigation", () => ({
  useSearchParams: vi.fn(() => new URLSearchParams("")),
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: ReactNode }) => <a href={href}>{children}</a>,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }),
}));

const docsState = vi.hoisted(() => ({
  data: [] as unknown[],
  isLoading: false,
  isError: false,
  error: null as unknown,
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({
    data: docsState.data,
    isLoading: docsState.isLoading,
    isError: docsState.isError,
    error: docsState.error,
  }),
  useDeleteProjectDocument: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/features/documents/components/DocumentUploadZone", () => ({
  DocumentUploadZone: () => <div data-testid="upload-zone" />,
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (sel: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    sel({ activeProject: { id: "p1", name: "Workspace" } }),
}));

describe("DocumentsPage", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    docsState.data = [];
    docsState.isLoading = false;
    docsState.isError = false;
    docsState.error = null;
  });

  it("shows scoped subtitle when a project is active", async () => {
    const { useSearchParams } = await import("next/navigation");
    vi.mocked(useSearchParams).mockReturnValue(
      new URLSearchParams("projectId=p1") as unknown as ReadonlyURLSearchParams,
    );

    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <DocumentsPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/Workspace:\s*Workspace/)).toBeInTheDocument();
    expect(screen.getByTestId("upload-zone")).toBeInTheDocument();
  });

  it("shows empty state copy when there are no rows", async () => {
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <DocumentsPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/No documents yet/i)).toBeInTheDocument();
  });

  it("shows controlled API message when load fails with ApiError", async () => {
    docsState.isError = true;
    docsState.error = new ApiError(503, "Service temporarily unavailable");

    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <DocumentsPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(/Could not load documents/i);
    expect(screen.getByRole("alert")).toHaveTextContent(/Service temporarily unavailable/);
  });
});
