import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NewConversationDialog } from "./NewConversationDialog";
import { IntlTestProvider } from "@/test-utils/intl";

const mutateAsync = vi.fn();

vi.mock("@/features/chat/hooks/use-project-compatible-presets", async () => {
  const { compatiblePresetsQueryMock } = await import("@/test-utils/compatible-presets-mock");
  return {
    useProjectCompatiblePresets: () => compatiblePresetsQueryMock,
  };
});
vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({ data: [] }),
}));
vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useCreateConversation: () => ({ mutateAsync, isPending: false }),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", async () => {
  const { effectiveEmbeddingDefaultsMock } = await import("@/test-utils/compatible-presets-mock");
  return {
    useMeEffectiveEmbeddingDefaults: () => effectiveEmbeddingDefaultsMock,
  };
});

function renderDialog() {
  const client = new QueryClient();
  return render(
    <QueryClientProvider client={client}>
      <IntlTestProvider locale="en">
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

describe("NewConversationRetrievalDefaults", () => {
  it("sends initialRuntimeOverride when assistant defaults are selected", async () => {
    mutateAsync.mockResolvedValue({ id: "c1" });
    renderDialog();

    fireEvent.click(screen.getByTestId("new-conversation-use-assistant-retrieval-defaults"));
    fireEvent.click(screen.getByTestId("chat-new-conversation-create"));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          initialRuntimeOverride: {
            retrievalOverrideMode: "assistant_defaults",
          },
        }),
      );
    });
  });

  it("omits initialRuntimeOverride by default", async () => {
    mutateAsync.mockResolvedValue({ id: "c1" });
    renderDialog();
    fireEvent.click(screen.getByTestId("chat-new-conversation-create"));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.not.objectContaining({
          initialRuntimeOverride: expect.anything(),
        }),
      );
    });
  });
});
