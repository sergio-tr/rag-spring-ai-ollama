import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
  apiProductPath: (p: string) => {
    const seg = p.startsWith("/") ? p : `/${p}`;
    return `/api/v5${seg}`;
  },
}));

import { PresetsSettingsPanel } from "./PresetsSettingsPanel";

function renderWithQuery(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("PresetsSettingsPanel", () => {
  let clipboardSpy: ReturnType<typeof vi.spyOn> | undefined;

  beforeEach(() => {
    apiFetchMock.mockReset();
    clipboardSpy = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined as never);
    apiFetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "GET" && u.includes("/config/schema")) {
        return {
          version: 1,
          fields: [
            { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
            { key: "llmModel", type: "string", userEditable: true },
          ],
        };
      }
      if (method === "GET" && u.endsWith("/presets")) {
        return [];
      }
      if (method === "POST" && u.endsWith("/presets")) {
        const body = JSON.parse(String(init?.body)) as {
          name: string;
          description: string | null;
          tags: string[];
          values: Record<string, unknown>;
        };
        return {
          id: "preset-new",
          name: body.name,
          description: body.description,
          tags: body.tags ?? [],
          values: body.values,
          system: false,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        };
      }
      throw new Error(`unexpected ${method} ${u}`);
    });
  });

  afterEach(() => {
    clipboardSpy?.mockRestore();
  });

  it("does not show an editable values textarea outside Advanced import", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument();
    });
    const card = screen.getByTestId("presets-create-card");
    expect(within(card).getAllByTestId("preset-import-textarea")).toHaveLength(1);
  });

  it("renders structured configuration summary without visible raw JSON in normal mode", async () => {
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("preset-draft-summary")).toBeInTheDocument();
      expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId("preset-draft-preview")).not.toBeInTheDocument();
    const summary = screen.getByTestId("preset-draft-summary");
    const card = within(summary).getByTestId("preset-profile-card-draft");
    const advancedDetails = within(card).getByText(/Advanced technical details/i).closest("details");
    expect(advancedDetails).not.toHaveAttribute("open");

    const topK = screen.getByLabelText(/passages to retrieve/i);
    await user.clear(topK);
    await user.type(topK, "5");

    await user.click(within(card).getByText(/Advanced technical details/i));
    expect(advancedDetails).toHaveAttribute("open");
    expect(within(card).getByText(/"topK": 5/)).toBeInTheDocument();
  });

  it("rejects import JSON with unsupported keys", async () => {
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Advanced.*import/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Advanced.*import/i));
    fireEvent.change(screen.getByTestId("preset-import-textarea"), {
      target: { value: '{"topK":3,"badKey":true}' },
    });
    await user.click(screen.getByRole("button", { name: /validate and apply import/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/badKey/i);
  });

  it("submits merged preset values on create", async () => {
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument());

    await user.type(screen.getByLabelText(/^Name$/), "My preset");
    const topK = screen.getByLabelText(/passages to retrieve/i);
    await user.clear(topK);
    await user.type(topK, "9");
    await user.click(screen.getByRole("button", { name: /^create preset$/i }));

    await waitFor(() => {
      const posts = apiFetchMock.mock.calls.filter((c) => {
        const init = c[1] as RequestInit | undefined;
        return String(c[0]).includes("/presets") && init?.method === "POST";
      });
      expect(posts.length).toBeGreaterThan(0);
      const body = JSON.parse(String((posts[0][1] as RequestInit).body)) as {
        name: string;
        values: Record<string, unknown>;
      };
      expect(body.name).toBe("My preset");
      expect(body.values.topK).toBe(9);
    });
  });

  it("copies export JSON via clipboard", async () => {
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Advanced.*import/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Advanced.*import/i));
    await user.click(screen.getByRole("button", { name: /copy payload json/i }));
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalled();
    });
  });
});
