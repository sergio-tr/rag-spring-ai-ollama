import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { DocumentUploadZone } from "./DocumentUploadZone";
import { ApiError } from "@/lib/api-client";

const uploadHook = vi.hoisted(() => ({
  mutateAsync: vi.fn().mockResolvedValue(undefined),
  isError: false,
  isPending: false,
  error: null as unknown,
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useUploadProjectDocument: () => ({
    mutateAsync: uploadHook.mutateAsync,
    isError: uploadHook.isError,
    isPending: uploadHook.isPending,
    error: uploadHook.error,
  }),
}));

describe("DocumentUploadZone", () => {
  beforeEach(() => {
    uploadHook.mutateAsync.mockClear();
    uploadHook.isError = false;
    uploadHook.isPending = false;
    uploadHook.error = null;
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

  it("maps 403 to unauthorized upload hint like 401", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(403, "forbidden");
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toContain("not authorized");
  });

  it("renders friendly error messages for common ApiError status codes", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(503, "x");
    const { rerender } = render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toContain("ollama");

    uploadHook.error = new ApiError(409, "x");
    rerender(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toContain("same filename");

    uploadHook.error = new ApiError(401, "x");
    rerender(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toContain("not authorized");

    uploadHook.error = new ApiError(504, "x");
    rerender(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toMatch(/gateway|unavailable/);
  });

  it("extracts title field from structured ApiError metadata", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(500, "fallback", {
      kind: "http",
      details: { title: "bad upload" } as unknown as Record<string, unknown>,
    });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("bad upload");
  });

  it("extracts error detail from JSON in ApiError message", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(500, JSON.stringify({ error: { message: "boom" } }));
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("boom");
  });

  it("prefers structured details from ApiError metadata", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(500, "fallback", {
      kind: "http",
      details: { error: { detail: "from details" } } as unknown as Record<string, unknown>,
    });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("from details");
  });

  it("maps network-kind ApiError to gateway message", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(0, "offline", { kind: "network" });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert").textContent?.toLowerCase()).toMatch(/gateway|unavailable/);
  });

  it("classifies HTML gateway errors without parsing message as JSON", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(502, "Gateway error.", {
      kind: "http",
      contentType: "text/html",
    });
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const alert = screen.getByRole("alert").textContent ?? "";
    expect(alert.toLowerCase()).toMatch(/gateway|reach|api/i);
    expect(alert).not.toContain("<html");
  });

  it("falls back to raw ApiError message when JSON parsing fails", () => {
    uploadHook.isError = true;
    uploadHook.error = new ApiError(500, "{not json");
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("{not json");
  });

  it("renders non-ApiError errors via their message", () => {
    uploadHook.isError = true;
    uploadHook.error = new Error("nope");
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("nope");
  });

  it("uploads multiple selected files sequentially", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <DocumentUploadZone projectId="p1" />
      </IntlTestProvider>,
    );
    const a = new File(["a"], "a.txt", { type: "text/plain" });
    const b = new File(["b"], "b.txt", { type: "text/plain" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, [a, b]);
    expect(uploadHook.mutateAsync).toHaveBeenCalledTimes(2);
    expect(uploadHook.mutateAsync).toHaveBeenCalledWith(a);
    expect(uploadHook.mutateAsync).toHaveBeenCalledWith(b);
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
