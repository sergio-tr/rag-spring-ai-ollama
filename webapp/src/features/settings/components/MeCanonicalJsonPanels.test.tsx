import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MeCanonicalJsonPanels } from "./MeCanonicalJsonPanels";
import { IntlTestProvider } from "@/test-utils/intl";
import { apiFetch } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => {
    const seg = p.startsWith("/") ? p : `/${p}`;
    return `/api/v5${seg}`;
  },
}));

function wrapper({ children }: { children: ReactNode }) {
  return <IntlTestProvider>{children}</IntlTestProvider>;
}

describe("MeCanonicalJsonPanels", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockImplementation(async (url: string | URL, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "GET" && u.includes("/me/preferences")) {
        return { schemaVersion: 1, preferences: { locale: "en", customFlag: true } };
      }
      if (method === "GET" && u.includes("/me/personalization")) {
        return { schemaVersion: 1, personalization: { theme: "system", note: "keep-me" } };
      }
      if (method === "PUT" && u.includes("/me/preferences")) {
        const raw = init?.body ? (JSON.parse(String(init.body)) as { preferences?: Record<string, unknown> }) : {};
        return { schemaVersion: 1, preferences: raw.preferences ?? {} };
      }
      if (method === "PUT" && u.includes("/me/personalization")) {
        const raw = init?.body
          ? (JSON.parse(String(init.body)) as { personalization?: Record<string, unknown> })
          : {};
        return { schemaVersion: 1, personalization: raw.personalization ?? {} };
      }
      throw new Error(`unexpected fetch ${method} ${u}`);
    });
  });

  it("does not render editable JSON textareas", async () => {
    const { container } = render(<MeCanonicalJsonPanels />, { wrapper });
    await waitFor(() => {
      expect(screen.getByTestId("me-canonical-panels")).toBeInTheDocument();
    });
    expect(container.querySelector("textarea")).toBeNull();
  });

  it("hydrates locale from preferences JSON", async () => {
    render(<MeCanonicalJsonPanels />, { wrapper });
    await waitFor(() => {
      const select = screen.getByLabelText(/language/i) as HTMLSelectElement;
      expect(select.value).toBe("en");
    });
  });

  it("preserves unknown keys when saving preferences", async () => {
    const user = userEvent.setup();
    render(<MeCanonicalJsonPanels />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText(/language/i)).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText(/language/i), "es");
    await user.click(screen.getAllByRole("button", { name: /^save$/i })[0]);

    await waitFor(() => {
      const putCalls = vi.mocked(apiFetch).mock.calls.filter((c) => {
        const init = c[1] as RequestInit | undefined;
        return String(c[0]).includes("/me/preferences") && init?.method === "PUT";
      });
      expect(putCalls.length).toBeGreaterThan(0);
      const body = JSON.parse(String((putCalls[0][1] as RequestInit).body)) as {
        preferences: Record<string, unknown>;
      };
      expect(body.preferences.locale).toBe("es");
      expect(body.preferences.customFlag).toBe(true);
    });
  });

  it("preserves unknown keys when saving personalization", async () => {
    const user = userEvent.setup();
    render(<MeCanonicalJsonPanels />, { wrapper });
    await waitFor(() => expect(screen.getByRole("button", { name: /^dark$/i })).toBeInTheDocument());

    const heading = screen.getByRole("heading", { level: 2, name: /^personalization$/i });
    const section = heading.closest("section");
    expect(section).not.toBeNull();
    await user.click(within(section!).getByRole("button", { name: /^dark$/i }));
    await user.click(within(section!).getByRole("button", { name: /^save$/i }));

    await waitFor(() => {
      const putCalls = vi.mocked(apiFetch).mock.calls.filter((c) => {
        const init = c[1] as RequestInit | undefined;
        return String(c[0]).includes("/me/personalization") && init?.method === "PUT";
      });
      expect(putCalls.length).toBeGreaterThan(0);
      const body = JSON.parse(String((putCalls[0][1] as RequestInit).body)) as {
        personalization: Record<string, unknown>;
      };
      expect(body.personalization.theme).toBe("dark");
      expect(body.personalization.note).toBe("keep-me");
    });
  });

  it("shows a warning when stored locale is unsupported", async () => {
    vi.mocked(apiFetch).mockImplementation(async (url: string | URL) => {
      const u = String(url);
      if (u.includes("/me/preferences")) {
        return { schemaVersion: 1, preferences: { locale: "fr" } };
      }
      if (u.includes("/me/personalization")) {
        return { schemaVersion: 1, personalization: {} };
      }
      throw new Error(u);
    });

    render(<MeCanonicalJsonPanels />, { wrapper });
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("fr");
    });
  });
});
