import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  authApiPath: (path: string) => `/api/test/auth${path.startsWith("/") ? path : `/${path}`}`,
  ApiError: class ApiError extends Error {},
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

let mockToken = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockToken ? `token=${encodeURIComponent(mockToken)}` : ""),
}));

import { ConfirmEmailView } from "./ConfirmEmailView";

describe("ConfirmEmailView", () => {
  beforeEach(() => {
    mockToken = "";
    apiFetch.mockReset();
  });

  it("shows missing token error", async () => {
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("posts confirm email when token exists", async () => {
    mockToken = "t1";
    apiFetch.mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(apiFetch).toHaveBeenCalled());
  });
});

