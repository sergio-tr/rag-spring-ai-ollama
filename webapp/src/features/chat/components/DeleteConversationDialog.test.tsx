import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { DeleteConversationDialog } from "./DeleteConversationDialog";

const mutateAsync = vi.fn();

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useDeleteConversation: () => ({
    mutateAsync,
    isPending: false,
    reset: vi.fn(),
  }),
}));

describe("DeleteConversationDialog", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue(undefined);
  });

  it("cancel does not call delete API", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider locale="en">
          <DeleteConversationDialog
            open
            onOpenChange={onOpenChange}
            projectId="p1"
            conversationId="c1"
            conversationTitle="Hello"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^Cancel$/i }));
    expect(mutateAsync).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("confirm calls delete mutation", async () => {
    const user = userEvent.setup();
    const onDeleted = vi.fn();
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider locale="en">
          <DeleteConversationDialog
            open
            onOpenChange={() => {}}
            projectId="p1"
            conversationId="c1"
            conversationTitle="Hello"
            onDeleted={onDeleted}
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const dlg = screen.getByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Delete chat$/i }));
    expect(mutateAsync).toHaveBeenCalledWith("c1");
    expect(onDeleted).toHaveBeenCalled();
  });

  it("shows controlled error when delete fails", async () => {
    mutateAsync.mockRejectedValueOnce(new Error("nope"));
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider locale="en">
          <DeleteConversationDialog
            open
            onOpenChange={() => {}}
            projectId="p1"
            conversationId="c1"
            conversationTitle="Hello"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const dlg = screen.getByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Delete chat$/i }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
