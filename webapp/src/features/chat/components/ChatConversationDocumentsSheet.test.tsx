import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConversationDocumentsSheet } from "./ChatConversationDocumentsSheet";

describe("ChatConversationDocumentsSheet", () => {
  const docs = [
    {
      id: "d1",
      fileName: "a.pdf",
      status: "READY" as const,
      chunkCount: 1,
      errorMessage: null,
      uploadedAt: "",
      reindexedAt: null,
      corpusScope: "PROJECT_SHARED" as const,
      conversationId: null,
      currentIndexSnapshotId: null,
      indexSignatureHash: null,
      storagePresent: true,
    },
  ];

  it("lists documents and forwards toggle", async () => {
    const user = userEvent.setup();
    const onDocToggle = vi.fn();
    render(
      <IntlTestProvider locale="en">
        <ChatConversationDocumentsSheet
          open
          onOpenChange={() => {}}
          projectName="Proj"
          docs={docs}
          limitDocs
          selectedDocIds={[]}
          patchPending={false}
          uploadPending={false}
          uploadError={null}
          uploadNotice={null}
          onDocToggle={onDocToggle}
          onUploadFiles={() => {}}
        />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("checkbox", { name: /Include a\.pdf/i }));
    expect(onDocToggle).toHaveBeenCalledWith("d1", true);
  });

  it("shows upload error alert", async () => {
    render(
      <IntlTestProvider locale="en">
        <ChatConversationDocumentsSheet
          open
          onOpenChange={() => {}}
          projectName="Proj"
          docs={docs}
          limitDocs={false}
          selectedDocIds={[]}
          patchPending={false}
          uploadPending={false}
          uploadError="Something failed"
          uploadNotice={null}
          onDocToggle={() => {}}
          onUploadFiles={() => {}}
        />
      </IntlTestProvider>,
    );
    // ScrollArea (Radix) schedules layout state after mount; waitFor wraps updates in act(...).
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Something failed");
    });
  });

  it("forwards file selection to upload handler", async () => {
    const user = userEvent.setup();
    const onUploadFiles = vi.fn();
    render(
      <IntlTestProvider locale="en">
        <ChatConversationDocumentsSheet
          open
          onOpenChange={() => {}}
          projectName="Proj"
          docs={docs}
          limitDocs={false}
          selectedDocIds={[]}
          patchPending={false}
          uploadPending={false}
          uploadError={null}
          uploadNotice={null}
          onDocToggle={() => {}}
          onUploadFiles={onUploadFiles}
        />
      </IntlTestProvider>,
    );
    const input = screen.getByLabelText(/Upload files to project/i);
    const file = new File(["x"], "z.txt", { type: "text/plain" });
    await user.upload(input, file);
    expect(onUploadFiles).toHaveBeenCalled();
    const arg = onUploadFiles.mock.calls[0][0] as FileList;
    expect(arg?.[0]?.name).toBe("z.txt");
  });
});
