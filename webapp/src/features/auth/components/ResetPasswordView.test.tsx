import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  ApiError: class ApiError extends Error {},
}));

const replace = vi.fn();
vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
  useRouter: () => ({ replace }),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(""),
}));

import { ResetPasswordView } from "./ResetPasswordView";

describe("ResetPasswordView", () => {
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
    vi.doMock("next/navigation", () => ({
      useSearchParams: () => new URLSearchParams("token=t1"),
    }));
    const { ResetPasswordView: ViewWithToken } = await import("./ResetPasswordView");
    apiFetch.mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ViewWithToken />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/^password$/i), "12345678");
    await user.click(screen.getByRole("button", { name: /set new password/i }));
    expect(apiFetch).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/login");
  });
});

