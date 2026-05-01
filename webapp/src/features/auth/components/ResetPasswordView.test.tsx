import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api-client";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const replace = vi.fn();
vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
  useRouter: () => ({ replace }),
}));

let mockToken = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockToken ? `token=${encodeURIComponent(mockToken)}` : ""),
}));

import { apiFetch, authApiPath } from "@/lib/api-client";
import { ResetPasswordView } from "./ResetPasswordView";

describe("ResetPasswordView", () => {
  beforeEach(() => {
    mockToken = "";
    vi.mocked(apiFetch).mockReset();
    replace.mockReset();
  });

  it("shows missing token message and disables submit", () => {
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /set new password/i })).toBeDisabled();
  });

  it("one icon toggle controls password and repeat-password visibility", async () => {
    mockToken = "t1";
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    const pwd = screen.getByLabelText(/^password$/i);
    const repeat = screen.getByLabelText(/repeat password/i);
    expect(screen.getByRole("button", { name: /show password/i })).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByRole("button", { name: /show repeated password/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /show password/i }));
    expect(pwd).toHaveAttribute("type", "text");
    expect(repeat).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: /hide password/i }));
    expect(pwd).toHaveAttribute("type", "password");
    expect(repeat).toHaveAttribute("type", "password");
  });

  it("blocks mismatched repeat password client-side without calling API", async () => {
    mockToken = "t1";
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "87654321");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/do not match/i),
    );
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("posts reset-password and redirects to login after success", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    const [url] = vi.mocked(apiFetch).mock.calls[0]!;
    expect(url).toBe(authApiPath("/reset-password"));
    expect(url).toBe("/api/v5/auth/reset-password");
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"), { timeout: 4000 });
  });

  it("shows localized message for expired reset token code", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, "Bad request", {
        kind: "http",
        rawBodyPreview: JSON.stringify({ code: "RESET_TOKEN_EXPIRED" }),
      }),
    );

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/expired/i);
    });
    expect(screen.getByRole("button", { name: /set new password/i })).not.toBeDisabled();
  });

  it("shows invalid token message for RESET_TOKEN_INVALID", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, "Bad request", {
        kind: "http",
        rawBodyPreview: JSON.stringify({ code: "RESET_TOKEN_INVALID" }),
      }),
    );
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/invalid/i),
    );
  });

  it("shows reused token message for RESET_TOKEN_ALREADY_USED", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, "Bad request", {
        kind: "http",
        rawBodyPreview: JSON.stringify({ code: "RESET_TOKEN_ALREADY_USED" }),
      }),
    );
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/already used/i),
    );
  });

  it("disables submit after successful reset", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /set new password/i })).toBeDisabled();
    });
  });

  it("shows success status copy after reset", async () => {
    mockToken = "t1";
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/password updated/i),
    );
  });
});
