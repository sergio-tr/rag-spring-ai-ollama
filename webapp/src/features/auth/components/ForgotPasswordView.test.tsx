import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

import { apiFetch, authApiPath } from "@/lib/api-client";
import { ForgotPasswordView } from "./ForgotPasswordView";

describe("ForgotPasswordView", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("submits an email and shows neutral success status", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const user = userEvent.setup();

    render(
      <IntlTestProvider>
        <ForgotPasswordView />
      </IntlTestProvider>,
    );

    await user.type(screen.getByLabelText(/^email$/i), "user@example.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));
    expect(apiFetch).toHaveBeenCalledTimes(1);
    const [url, init] = vi.mocked(apiFetch).mock.calls[0]!;
    expect(url).toBe(authApiPath("/forgot-password"));
    expect(url).toBe("/api/v5/auth/forgot-password");
    expect(init).toMatchObject({
      method: "POST",
      skipCredentials: true,
    });
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({
      email: "user@example.com",
      locale: "en",
    });
    expect(screen.getByRole("status")).toHaveTextContent(/reset link will be sent/i);
    expect(screen.getByRole("button", { name: /send reset link/i })).toBeDisabled();
  });

  it("shows network error in status region when submit fails", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("boom"));
    const user = userEvent.setup();

    render(
      <IntlTestProvider>
        <ForgotPasswordView />
      </IntlTestProvider>,
    );

    await user.type(screen.getByLabelText(/^email$/i), "user@example.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/network error/i),
    );
  });
});
