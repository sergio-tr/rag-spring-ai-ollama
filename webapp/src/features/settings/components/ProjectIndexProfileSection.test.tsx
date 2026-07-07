import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ProjectIndexProfileSection } from "./ProjectIndexProfileSection";

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: vi.fn(),
}));

vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: vi.fn(),
}));

import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";

function renderSection() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider locale="en">
        <ProjectIndexProfileSection projectId="p1" />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("ProjectIndexProfileSection", () => {
  beforeEach(() => {
    vi.mocked(useMeEffectiveEmbeddingDefaults).mockReturnValue({
      data: { embeddingModel: "nomic-embed-text" },
      isLoading: false,
      isError: false,
    } as never);
  });

  it("shows structured search legacy warning for STRUCT projects", () => {
    vi.mocked(useProjectIndexProfile).mockReturnValue({
      data: {
        projectId: "p1",
        embeddingModelId: "nomic-embed-text",
        materializationStrategy: "STRUCTURED_SEARCH",
        metadataEnabled: true,
        metadataProfile: null,
        chunkMaxChars: 2048,
        chunkOverlap: 128,
        profileHash: "abc123",
        createdAt: "",
        updatedAt: "",
      },
      isLoading: false,
      isError: false,
    } as never);
    renderSection();
    expect(screen.getByTestId("project-index-profile-structured-search-warning")).toHaveTextContent(
      /not intended for standard RAG chat over documents/i,
    );
  });

  it("shows active index profile values", () => {
    vi.mocked(useProjectIndexProfile).mockReturnValue({
      data: {
        projectId: "p1",
        embeddingModelId: "nomic-embed-text",
        materializationStrategy: "CHUNK_LEVEL",
        metadataEnabled: true,
        metadataProfile: null,
        chunkMaxChars: 2048,
        chunkOverlap: 128,
        profileHash: "abc123",
        createdAt: "",
        updatedAt: "",
      },
      isLoading: false,
      isError: false,
    } as never);

    renderSection();
    expect(screen.getByTestId("project-index-profile-section")).toBeInTheDocument();
    expect(screen.getByText("nomic-embed-text")).toBeInTheDocument();
    expect(screen.getByTestId("project-index-profile-metadata-capability")).toHaveTextContent(/Enabled/i);
    expect(screen.queryByTestId("project-index-profile-drift-badge")).not.toBeInTheDocument();
  });

  it("shows drift badge when user default embedding differs from active snapshot", () => {
    vi.mocked(useMeEffectiveEmbeddingDefaults).mockReturnValue({
      data: { embeddingModel: "bge-m3" },
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(useProjectIndexProfile).mockReturnValue({
      data: {
        projectId: "p1",
        embeddingModelId: "nomic-embed-text",
        materializationStrategy: "CHUNK_LEVEL",
        metadataEnabled: false,
        metadataProfile: null,
        chunkMaxChars: 1024,
        chunkOverlap: null,
        profileHash: "hash",
        createdAt: "",
        updatedAt: "",
      },
      isLoading: false,
      isError: false,
    } as never);

    renderSection();
    expect(screen.getByTestId("project-index-profile-drift-badge")).toBeInTheDocument();
  });

  it("shows fixed-index copy instead of reindex guidance", () => {
    vi.mocked(useProjectIndexProfile).mockReturnValue({
      data: {
        projectId: "p1",
        embeddingModelId: "nomic-embed-text",
        materializationStrategy: "CHUNK_LEVEL",
        metadataEnabled: false,
        metadataProfile: null,
        chunkMaxChars: 1024,
        chunkOverlap: null,
        profileHash: "hash",
        createdAt: "",
        updatedAt: "",
      },
      isLoading: false,
      isError: false,
    } as never);

    renderSection();
    expect(screen.getByTestId("project-index-profile-fixed-hint")).toHaveTextContent(
      /Index settings are fixed after project creation/i,
    );
    expect(screen.queryByText(/reindex/i)).not.toBeInTheDocument();
  });
});
