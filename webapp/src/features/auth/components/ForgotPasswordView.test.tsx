import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  authApiPath: (path: string) => `/api/test/auth${path.startsWith("/") ? path : `/${path}`}`,
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

import { ForgotPasswordView } from "./ForgotPasswordView";

describe("ForgotPasswordView", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("submits an email and shows success message", async () => {
    apiFetch.mockResolvedValueOnce({});
    const user = userEvent.setup();

    render(
      <IntlTestProvider>
        <ForgotPasswordView />
      </IntlTestProvider>,
    );

    await user.type(screen.getByLabelText(/^email$/i), "user@example.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));
    expect(apiFetch).toHaveBeenCalled();
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /send reset link/i })).toBeDisabled();
  });

  it("shows a network error when submit fails", async () => {
    apiFetch.mockRejectedValueOnce(new Error("boom"));
    const user = userEvent.setup();

    render(
      <IntlTestProvider>
        <ForgotPasswordView />
      </IntlTestProvider>,
    );

    await user.type(screen.getByLabelText(/^email$/i), "user@example.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});

