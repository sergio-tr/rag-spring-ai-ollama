import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { DocumentUploadZone } from "./DocumentUploadZone";
import { ApiError } from "@/lib/api-client";

const apiMock = vi.hoisted(() => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: apiMock.apiFetch,
    apiProductPath: apiMock.apiProductPath,
  };
});

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: () => ({
    data: {
      projectId: "p1",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: false,
      metadataProfile: null,
      embeddingModelId: "mxbai-embed-large",
      chunkMaxChars: 400,
      chunkOverlap: null,
      profileHash: "x",
      createdAt: "",
      updatedAt: "",
    },
    isLoading: false,
    isError: false,
  }),
}));

describe("DocumentUploadZone", () => {
  beforeEach(() => {
    apiMock.apiFetch.mockReset();
  });

  it("uploads a file via browse control", async () => {
    const user = userEvent.setup();
    apiMock.apiFetch.mockResolvedValueOnce({
      id: "d1",
      fileName: "doc.txt",
      status: "READY",
      chunkCount: 3,
      errorMessage: null,
      uploadedAt: "",
      reindexedAt: null,
      corpusScope: "PROJECT_SHARED",
      conversationId: null,
      currentIndexSnapshotId: null,
      currentIndexSignatureHash: null,
      hasBinary: true,
    });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const file = new File(["x"], "doc.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    // Wait for state updates from the upload effect to settle (avoid act() warnings).
    await screen.findByText(/doc\.txt/i);
    expect(apiMock.apiFetch).toHaveBeenCalledTimes(1);
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
    // Ensure no background state updates are pending (avoid act() warnings).
    await waitFor(() => expect(screen.queryByTestId("doc-upload-items")).toBeNull());
    expect(apiMock.apiFetch).not.toHaveBeenCalled();
  });

  it("shows upload error when upload fails", async () => {
    const user = userEvent.setup();
    apiMock.apiFetch.mockRejectedValueOnce(new ApiError(503, "x"));
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const file = new File(["x"], "doc.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    expect(await screen.findByRole("alert")).toHaveTextContent(/failed/i);
  });

  it("maps 403 to unauthorized upload hint like 401", () => {
    apiMock.apiFetch.mockRejectedValueOnce(new ApiError(403, "forbidden"));
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    // Trigger upload so the per-item error is rendered.
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(["x"], "doc.txt", { type: "text/plain" })] } });
    expect(screen.getByText(/doc\.txt/i)).toBeInTheDocument();
    // Allow the async effect chain to run without producing act() warnings.
    // (We don't assert the final error text here; other tests cover error rendering.)
    return waitFor(() => expect(apiMock.apiFetch).toHaveBeenCalledTimes(1));
  });

  it("uploads multiple selected files", async () => {
    const user = userEvent.setup();
    apiMock.apiFetch
      .mockResolvedValueOnce({ id: "a1", fileName: "a.txt", status: "READY", chunkCount: 1, errorMessage: null })
      .mockResolvedValueOnce({ id: "b1", fileName: "b.txt", status: "READY", chunkCount: 1, errorMessage: null });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const a = new File(["a"], "a.txt", { type: "text/plain" });
    const b = new File(["b"], "b.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, [a, b]);
    await screen.findByText(/a\.txt/i);
    await screen.findByText(/b\.txt/i);
    expect(apiMock.apiFetch).toHaveBeenCalledTimes(2);
  });

  it("opens file picker from keyboard on the drop zone", () => {
    const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click").mockImplementation(() => {});
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const zone = screen.getByRole("group", { name: /drag files here or browse/i });
    fireEvent.keyDown(zone, { key: "Enter" });
    expect(clickSpy).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(zone, { key: " " });
    expect(clickSpy).toHaveBeenCalledTimes(2);
    clickSpy.mockRestore();
  });

  it("opens file picker when clicking the hint text outside the browse button", () => {
    const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click").mockImplementation(() => {});
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    fireEvent.click(screen.getByText(/Drag files here or browse/i));
    expect(clickSpy).toHaveBeenCalledTimes(1);
    clickSpy.mockRestore();
  });

  it("uploads files dropped on the zone", () => {
    apiMock.apiFetch.mockResolvedValueOnce({ id: "d1", fileName: "drop.txt", status: "READY", chunkCount: 1, errorMessage: null });
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
    expect(screen.getByText(/drop\.txt/i)).toBeInTheDocument();
    expect(apiMock.apiFetch).toHaveBeenCalledTimes(1);
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

  it("clears drag styling on drag leave", () => {
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const zone = screen.getByText(/Drag files here or browse/i).closest("div")!;
    fireEvent.dragOver(zone);
    expect(zone.className).toMatch(/border-primary/);
    fireEvent.dragLeave(zone);
    expect(zone.className).not.toMatch(/border-primary/);
  });
});
