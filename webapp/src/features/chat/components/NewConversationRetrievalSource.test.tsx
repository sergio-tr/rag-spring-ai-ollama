import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NewConversationDialog } from "./NewConversationDialog";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/features/chat/hooks/use-project-compatible-presets", () => ({
  useProjectCompatiblePresets: () => ({
    data: { productPresets: [], experimentalPresets: [] },
    isError: false,
    isLoading: false,
  }),
}));
vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({ data: [] }),
}));
vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useCreateConversation: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: () => ({
    data: {
      retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK" },
    },
  }),
}));

function renderDialog() {
  const client = new QueryClient();
  return render(
    <QueryClientProvider client={client}>
      <IntlTestProvider>
        <NewConversationDialog
          projectId="p1"
          open
          onOpenChange={() => {}}
          onCreated={async () => {}}
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("NewConversationRetrievalSource", () => {
  it("shows account or project retrieval defaults", () => {
    renderDialog();
    const values = screen.getByTestId("new-conversation-retrieval-values");
    expect(values.textContent).toContain("8");
    expect(values.textContent).toContain("Account or project defaults");
  });
});
