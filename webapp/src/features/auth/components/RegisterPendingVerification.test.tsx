import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api-client";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

const mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
}));

import { apiFetch, authApiPath } from "@/lib/api-client";
import { RegisterPendingVerification } from "./RegisterPendingVerification";

describe("RegisterPendingVerification", () => {
  beforeEach(() => {
    mockSearchParams.delete("email");
    mockSearchParams.set("email", "user@example.com");
    vi.mocked(apiFetch).mockReset();
  });

  it("does not call session or any auth API on mount", () => {
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("shows destination email from query string", () => {
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    expect(screen.getByText("user@example.com")).toBeInTheDocument();
    expect(screen.getByText(/Sent to:/i)).toBeInTheDocument();
  });

  it("calls resend-confirmation with email and locale", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /resend confirmation email/i }));
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));
    const [url, init] = vi.mocked(apiFetch).mock.calls[0]!;
    expect(url).toBe(authApiPath("/resend-confirmation"));
    expect(init).toMatchObject({
      method: "POST",
      skipCredentials: true,
    });
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({
      email: "user@example.com",
      locale: "en",
    });
  });

  it("shows neutral success copy after resend", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /resend confirmation email/i }));
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/eligible/i),
    );
  });

  it("shows failure UX after ApiError", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(429, "slow down"));
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /resend confirmation email/i }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/could not resend/i),
    );
  });

  it("shows network error on non-ApiError failure", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("offline"));
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /resend confirmation email/i }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/network error/i),
    );
  });

  it("shows missing-email alert and disables resend when email param absent", () => {
    mockSearchParams.delete("email");
    render(
      <IntlTestProvider>
        <RegisterPendingVerification />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/missing email/i);
    expect(screen.getByRole("button", { name: /resend confirmation email/i })).toBeDisabled();
  });
});
