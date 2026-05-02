import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import {
  DELETE_ALL_PROJECT_DOCUMENTS_PHRASE,
  DeleteAllProjectDocumentsDialog,
} from "./DeleteAllProjectDocumentsDialog";

const mutateAsyncMock = vi.fn(async () => {});
const resetMock = vi.fn();

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useDeleteAllProjectDocuments: () => ({
    mutateAsync: mutateAsyncMock,
    reset: resetMock,
    isPending: false,
    isError: false,
    error: null,
  }),
}));

describe("DeleteAllProjectDocumentsDialog", () => {
  beforeEach(() => {
    mutateAsyncMock.mockClear();
    resetMock.mockClear();
  });

  it("requires exact phrase before confirming", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    render(
      <IntlTestProvider locale="en">
        <DeleteAllProjectDocumentsDialog
          open
          onOpenChange={onOpenChange}
          projectId="p1"
          projectName="Demo"
        />
      </IntlTestProvider>,
    );

    const confirm = screen.getByRole("button", { name: /^delete all$/i });
    expect(confirm).toBeDisabled();

    await user.type(screen.getByPlaceholderText(DELETE_ALL_PROJECT_DOCUMENTS_PHRASE), "wrong");
    expect(confirm).toBeDisabled();

    await user.clear(screen.getByPlaceholderText(DELETE_ALL_PROJECT_DOCUMENTS_PHRASE));
    await user.type(screen.getByPlaceholderText(DELETE_ALL_PROJECT_DOCUMENTS_PHRASE), DELETE_ALL_PROJECT_DOCUMENTS_PHRASE);
    expect(confirm).toBeEnabled();

    await user.click(confirm);
    expect(mutateAsyncMock).toHaveBeenCalledTimes(1);
  });
});
