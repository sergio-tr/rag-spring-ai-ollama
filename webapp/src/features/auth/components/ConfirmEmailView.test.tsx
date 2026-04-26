import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  ApiError: class ApiError extends Error {},
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(""),
}));

import { ConfirmEmailView } from "./ConfirmEmailView";

describe("ConfirmEmailView", () => {
  it("shows missing token error", async () => {
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("posts confirm email when token exists", async () => {
    vi.doMock("next/navigation", () => ({
      useSearchParams: () => new URLSearchParams("token=t1"),
    }));
    const { ConfirmEmailView: ViewWithToken } = await import("./ConfirmEmailView");
    apiFetch.mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <ViewWithToken />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(apiFetch).toHaveBeenCalled());
  });
});

