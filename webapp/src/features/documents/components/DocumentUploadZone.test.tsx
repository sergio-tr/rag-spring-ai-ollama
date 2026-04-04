import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { DocumentUploadZone } from "./DocumentUploadZone";

const uploadHook = vi.hoisted(() => ({
  mutateAsync: vi.fn().mockResolvedValue(undefined),
  isError: false,
  isPending: false,
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useUploadProjectDocument: () => ({
    mutateAsync: uploadHook.mutateAsync,
    isError: uploadHook.isError,
    isPending: uploadHook.isPending,
  }),
}));

describe("DocumentUploadZone", () => {
  beforeEach(() => {
    uploadHook.mutateAsync.mockClear();
    uploadHook.isError = false;
    uploadHook.isPending = false;
  });

  it("uploads a file via browse control", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const file = new File(["x"], "doc.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    expect(uploadHook.mutateAsync).toHaveBeenCalledWith(file);
  });

  it("does not upload when projectId is missing", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId={undefined} />
      </IntlTestProvider>,
    );
    const file = new File(["x"], "doc.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    expect(uploadHook.mutateAsync).not.toHaveBeenCalled();
  });

  it("shows upload error when mutation failed", () => {
    uploadHook.isError = true;
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/Upload failed/i);
  });

  it("uploads files dropped on the zone", () => {
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const zone = screen.getByText(/Drag files here or browse/i).closest("div");
    expect(zone).toBeTruthy();
    const file = new File(["d"], "drop.txt", { type: "text/plain" });
    // jsdom has no DataTransfer; pass a minimal dataTransfer.files array-like for onDrop
    const files = { 0: file, length: 1, item: (i: number) => (i === 0 ? file : null) };
    fireEvent.drop(zone!, { dataTransfer: { files } });
    expect(uploadHook.mutateAsync).toHaveBeenCalledWith(file);
  });

  it("sets drag styling on drag over and clears on drop", () => {
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const zone = screen.getByText(/Drag files here or browse/i).closest("div")!;
    fireEvent.dragOver(zone);
    expect(zone.className).toMatch(/border-primary/);
    const file = new File(["x"], "x.txt", { type: "text/plain" });
    const files = { 0: file, length: 1, item: (i: number) => (i === 0 ? file : null) };
    fireEvent.drop(zone, { dataTransfer: { files } });
    expect(zone.className).not.toMatch(/border-primary/);
  });
});
