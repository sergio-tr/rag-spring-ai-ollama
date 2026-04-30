import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  authApiPath: (path: string) => `/api/test/auth${path.startsWith("/") ? path : `/${path}`}`,
  ApiError: class ApiError extends Error {},
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
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("submits when token exists and redirects to login", async () => {
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
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });
});

