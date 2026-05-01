import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { ApiError } from "@/lib/api-client";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  authApiPath: (path: string) => `/api/test/auth${path.startsWith("/") ? path : `/${path}`}`,
  ApiError: class ApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public meta?: { rawBodyPreview?: string },
    ) {
      super(message);
      this.name = "ApiError";
    }
  },
}));

const replace = vi.fn();
vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
  useRouter: () => ({ replace }),
}));

let mockToken = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockToken ? `token=${encodeURIComponent(mockToken)}` : ""),
}));

import { ResetPasswordView } from "./ResetPasswordView";

describe("ResetPasswordView", () => {
  beforeEach(() => {
    mockToken = "";
    apiFetch.mockReset();
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

  it("submits when token exists and redirects to login after success", async () => {
    mockToken = "t1";
    apiFetch.mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ResetPasswordView />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.type(screen.getByLabelText(/repeat password/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    expect(apiFetch).toHaveBeenCalled();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"), { timeout: 4000 });
  });

  it("shows localized message for expired reset token code", async () => {
    mockToken = "t1";
    apiFetch.mockRejectedValueOnce(
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

  it("disables submit after successful reset", async () => {
    mockToken = "t1";
    apiFetch.mockResolvedValueOnce({});

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
});

