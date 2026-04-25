import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { LoginForm } from "./LoginForm";
import { RegisterForm } from "./RegisterForm";

const push = vi.fn();
const refresh = vi.fn();

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
  useRouter: () => ({ push, refresh }),
}));

vi.mock("@/features/auth/lib/session-client", () => ({
  commitSessionCookie: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

import { apiFetch } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";

describe("LoginForm", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    push.mockReset();
  });

  it("submits credentials and navigates to projects", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accessToken: "a",
      refreshToken: "r",
      user: { id: "1", email: "e@e.com", name: "n", role: "USER" },
    });
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() => expect(commitSessionCookie).toHaveBeenCalled());
    expect(push).toHaveBeenCalledWith("/projects");
  });

  it("shows network error on generic failure", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("offline"));
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("shows error on invalid credentials", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, ""));
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "badpass12");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("renders forgot password link", async () => {
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("link", { name: /forgot password/i })).toBeInTheDocument();
  });
});

describe("RegisterForm", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    push.mockReset();
  });

  it("registers and navigates", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accessToken: "a",
      refreshToken: "r",
      user: { id: "1", email: "e@e.com", name: "n", role: "USER" },
    });
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "secret12");
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() => expect(commitSessionCookie).toHaveBeenCalled());
    expect(push).toHaveBeenCalledWith("/projects");
  });

  it("shows register error on conflict", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(409, ""));
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "secret12");
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });
});
