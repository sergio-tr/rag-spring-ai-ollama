import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
  apiProductPath: (p: string) => {
    const seg = p.startsWith("/") ? p : `/${p}`;
    return `/api/v5${seg}`;
  },
}));

import { SettingsDataPanel } from "./SettingsDataPanel";

function renderPanel(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <IntlTestProvider>
      <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
    </IntlTestProvider>,
  );
}

describe("SettingsDataPanel", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("shows user-facing summary and inventory copy without exposing GET paths in the main cards", async () => {
    apiFetchMock.mockImplementation(async (url: string | URL) => {
      const u = String(url);
      if (u.includes("/me/summary")) {
        return {
          projectCount: 1,
          conversationCount: 2,
          documentCount: 3,
          estimatedStorageBytes: 99,
        };
      }
      if (u.includes("/me/documents")) {
        return {
          items: [
            {
              documentId: "d1",
              projectId: "p1",
              conversationId: null,
              corpusScope: "PROJECT_SHARED",
              fileName: "a.pdf",
              status: "READY",
              uploadedAt: "2025-01-01T00:00:00Z",
              reindexedAt: null,
            },
          ],
          total: 1,
        };
      }
      throw new Error(`unexpected fetch: ${u}`);
    });

    renderPanel(<SettingsDataPanel />);

    expect(screen.queryByText(/GET \/me\//)).toBeNull();

    await waitFor(() => {
      expect(screen.getByTestId("data-summary-description")).toHaveTextContent(
        /projects and conversations linked to your account/i,
      );
    });

    await waitFor(() => {
      expect(screen.getByTestId("data-inventory-description")).toHaveTextContent(
        /Indexed files across your projects/i,
      );
    });

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: /^file$/i })).toBeInTheDocument();
    });

    expect(screen.queryByText(/GET \/me\//)).toBeNull();
    expect(screen.getByText("a.pdf")).toBeInTheDocument();
  });

  it("renders controlled empty-state copy when the document list is empty", async () => {
    apiFetchMock.mockImplementation(async (url: string | URL) => {
      const u = String(url);
      if (u.includes("/me/summary")) {
        return {
          projectCount: 0,
          conversationCount: 0,
          documentCount: 0,
          estimatedStorageBytes: 0,
        };
      }
      if (u.includes("/me/documents")) {
        return { items: [], total: 0 };
      }
      throw new Error(`unexpected fetch: ${u}`);
    });

    renderPanel(<SettingsDataPanel />);

    await waitFor(() => {
      expect(screen.getByTestId("data-inventory-empty")).toHaveTextContent(
        /No documents yet/i,
      );
    });
  });

  it("shows understandable error copy when summary or documents fail to load", async () => {
    apiFetchMock.mockImplementation(async (url: string | URL) => {
      const u = String(url);
      if (u.includes("/me/summary")) {
        throw new Error("boom-summary");
      }
      if (u.includes("/me/documents")) {
        throw new Error("boom-docs");
      }
      throw new Error(`unexpected fetch: ${u}`);
    });

    renderPanel(<SettingsDataPanel />);

    const alerts = await screen.findAllByRole("alert");
    expect(alerts).toHaveLength(2);
    expect(alerts[0]).toHaveTextContent(/couldn't load your usage summary/i);
    expect(alerts[1]).toHaveTextContent(/couldn't load your documents/i);
  });

  it("mounts API path reference only after opening the technical details section", async () => {
    apiFetchMock.mockImplementation(async (url: string | URL) => {
      const u = String(url);
      if (u.includes("/me/summary")) {
        return {
          projectCount: 0,
          conversationCount: 0,
          documentCount: 0,
          estimatedStorageBytes: 0,
        };
      }
      if (u.includes("/me/documents")) {
        return { items: [], total: 0 };
      }
      throw new Error(`unexpected fetch: ${u}`);
    });

    renderPanel(<SettingsDataPanel />);

    await waitFor(() => expect(screen.getByTestId("data-inventory-empty")).toBeInTheDocument());

    expect(screen.queryByText(/GET `\/me\/summary`/)).toBeNull();

    fireEvent.click(screen.getByText("Technical reference (API)"));

    await waitFor(() => {
      expect(screen.getByText(/GET `\/me\/summary`/)).toBeInTheDocument();
    });
    expect(screen.getByText(/GET `\/me\/documents`/)).toBeInTheDocument();
  });
});
