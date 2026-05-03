import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError } from "@/lib/api-client";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

let mockToken = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockToken ? `token=${encodeURIComponent(mockToken)}` : ""),
}));

import { apiFetch, authApiPath } from "@/lib/api-client";
import { ConfirmEmailView } from "./ConfirmEmailView";

describe("ConfirmEmailView", () => {
  beforeEach(() => {
    mockToken = "";
    vi.mocked(apiFetch).mockReset();
  });

  it("shows missing token error", async () => {
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(screen.getByRole("alert")).toHaveTextContent(/missing/i);
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("posts confirm-email when token exists", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));
    const [url, init] = vi.mocked(apiFetch).mock.calls[0]!;
    expect(url).toBe(authApiPath("/confirm-email"));
    expect(url).toBe("/api/v5/auth/confirm-email");
    expect(init).toMatchObject({
      method: "POST",
      skipCredentials: true,
    });
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ token: "t1" });
  });

  it("shows success state after confirmation", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() =>
      expect(screen.getByText(/email confirmed/i)).toBeInTheDocument(),
    );
  });

  it("shows confirm failure message on ApiError", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(400, "bad token"));
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/confirmation failed/i),
    );
  });

  it("shows network error on non-ApiError failure", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("offline"));
    render(
      <IntlTestProvider>
        <ConfirmEmailView />
      </IntlTestProvider>,
    );
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/network error/i),
    );
  });
});
