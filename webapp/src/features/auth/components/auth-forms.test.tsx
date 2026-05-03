import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api-client";
import { authApiPath } from "@/lib/api-client";
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

function expectGoogleOauthStartHref(href: string) {
  const parsed = href.startsWith("http://") || href.startsWith("https://")
    ? new URL(href)
    : new URL(href, "http://localhost");
  expect(parsed.pathname).toBe("/api/v5/auth/oauth/google/start");
  expect(parsed.searchParams.get("locale")).toBe("en");
  expect(parsed.pathname).not.toMatch(/^\/(en|es|fr|de|pt|it)\//);
}

describe("LoginForm", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    push.mockReset();
    vi.unstubAllEnvs();
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("hides Google CTA when oauth flag is disabled", () => {
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("oauth-google-cta")).not.toBeInTheDocument();
  });

  it("hides Google CTA when oauth flag is unset", () => {
    vi.unstubAllEnvs();
    Reflect.deleteProperty(process.env, "NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED");
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("oauth-google-cta")).not.toBeInTheDocument();
  });

  it("shows Google CTA when oauth flag is enabled and points to v5 start route", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    const googleLink = screen.getByRole("link", { name: "Continue with Google" });
    expect(googleLink).toBeInTheDocument();
    expect(googleLink.tagName).toBe("A");
    const href = googleLink.getAttribute("href") ?? "";
    expect(href).toContain(`${authApiPath("/oauth/google/start")}?locale=en`);
    expectGoogleOauthStartHref(href);
  });

  it("renders Google CTA as a plain anchor (no client-side router) so OAuth start is not locale-prefixed", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    const cta = screen.getByTestId("oauth-google-cta");
    expect(cta.tagName).toBe("A");
    expectGoogleOauthStartHref(cta.getAttribute("href") ?? "");
  });

  it("prefixes Google OAuth href with NEXT_PUBLIC_API_BASE_URL when set (direct webapp port)", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://127.0.0.1:9000");
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("oauth-google-cta")).toHaveAttribute(
      "href",
      "http://127.0.0.1:9000/api/v5/auth/oauth/google/start?locale=en",
    );
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
    await user.type(screen.getByLabelText(/^password$/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() => expect(commitSessionCookie).toHaveBeenCalled());
    expect(apiFetch).toHaveBeenCalledWith(
      authApiPath("/login"),
      expect.objectContaining({
        method: "POST",
        skipCredentials: true,
      }),
    );
    expect(commitSessionCookie).toHaveBeenCalledWith({
      accessToken: "a",
      refreshToken: "r",
    });
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
    await user.type(screen.getByLabelText(/^password$/i), "secret1234");
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
    await user.type(screen.getByLabelText(/^password$/i), "badpass12");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("shows email verification error when backend returns EMAIL_NOT_VERIFIED code", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(403, "Forbidden.", {
        kind: "http",
        rawBodyPreview: '{"code":"EMAIL_NOT_VERIFIED","message":"Forbidden"}',
      }),
    );
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() =>
      expect(screen.getByText(/Email verification required\./i)).toBeInTheDocument(),
    );
  });

  it("shows email verification error when 403 message indicates verification requirement", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(403, "Forbidden.", {
        kind: "http",
        rawBodyPreview: '{"message":"Email verification required"}',
      }),
    );
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() =>
      expect(screen.getByText(/Email verification required\./i)).toBeInTheDocument(),
    );
  });

  it("falls back to network error for 403 without verification signal", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(403, "Forbidden.", {
        kind: "http",
      }),
    );
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret1234");
    await user.click(screen.getByRole("button", { name: /^Continue$/i }));
    await vi.waitFor(() =>
      expect(screen.getByText(/Network error\. Check API URL and CORS\./i)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/Email verification required\./i)).not.toBeInTheDocument();
  });

  it("renders forgot password link", async () => {
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("link", { name: /forgot password/i })).toBeInTheDocument();
  });

  it("renders email and password fields", () => {
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
  });

  it("toggles login password visibility with icon button", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <LoginForm />
      </IntlTestProvider>,
    );
    const pwd = screen.getByLabelText(/^password$/i);
    expect(pwd).toHaveAttribute("type", "password");
    const showBtn = screen.getByRole("button", { name: /show password/i });
    expect(showBtn).toHaveAttribute("aria-pressed", "false");
    await user.click(showBtn);
    expect(pwd).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: /hide password/i })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: /hide password/i }));
    expect(pwd).toHaveAttribute("type", "password");
  });
});

describe("RegisterForm", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    push.mockReset();
    vi.mocked(commitSessionCookie).mockClear();
    vi.unstubAllEnvs();
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("hides Google CTA when oauth flag is disabled", () => {
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("oauth-google-cta")).not.toBeInTheDocument();
  });

  it("hides Google CTA when oauth flag is unset", () => {
    vi.unstubAllEnvs();
    Reflect.deleteProperty(process.env, "NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED");
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("oauth-google-cta")).not.toBeInTheDocument();
  });

  it("shows Google CTA when oauth flag is enabled and points to v5 start route", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    const googleLink = screen.getByRole("link", { name: "Continue with Google" });
    expect(googleLink).toBeInTheDocument();
    expect(googleLink.tagName).toBe("A");
    const href = googleLink.getAttribute("href") ?? "";
    expect(href).toContain(`${authApiPath("/oauth/google/start")}?locale=en`);
    expectGoogleOauthStartHref(href);
  });

  it("renders Google CTA as a plain anchor (no client-side router) so OAuth start is not locale-prefixed", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    const cta = screen.getByTestId("oauth-google-cta");
    expect(cta.tagName).toBe("A");
    expectGoogleOauthStartHref(cta.getAttribute("href") ?? "");
  });

  it("prefixes Google OAuth href with NEXT_PUBLIC_API_BASE_URL when set (direct webapp port)", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://127.0.0.1:9000");
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("oauth-google-cta")).toHaveAttribute(
      "href",
      "http://127.0.0.1:9000/api/v5/auth/oauth/google/start?locale=en",
    );
  });

  it("blocks registration when repeat password does not match without calling the API", async () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret99");
    await user.click(screen.getByRole("checkbox", { name: /privacy policy/i }));
    await user.click(screen.getByRole("checkbox", { name: /terms and conditions/i }));
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() =>
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument(),
    );
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("requires legal acceptance before submitting", async () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret12");
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() =>
      expect(screen.getAllByText(/must accept privacy policy and terms/i).length).toBeGreaterThan(0),
    );
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("uses one password visibility toggle for both password and repeat-password fields", async () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    const pwd = screen.getByLabelText(/^password$/i);
    const repeat = screen.getByLabelText(/repeat password/i);
    expect(pwd).toHaveAttribute("type", "password");
    expect(repeat).toHaveAttribute("type", "password");
    expect(screen.queryAllByRole("button", { name: /show password/i })).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: /^show password$/i }));
    expect(pwd).toHaveAttribute("type", "text");
    expect(repeat).toHaveAttribute("type", "text");

    await user.click(screen.getByRole("button", { name: /^hide password$/i }));
    expect(pwd).toHaveAttribute("type", "password");
    expect(repeat).toHaveAttribute("type", "password");
  });

  it("registers and navigates", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValueOnce({
      status: "REGISTERED",
      login: {
        accessToken: "a",
        refreshToken: "r",
        user: { id: "1", email: "e@e.com", name: "n", role: "USER" },
      },
    });
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret12");
    await user.click(screen.getByRole("checkbox", { name: /privacy policy/i }));
    await user.click(screen.getByRole("checkbox", { name: /terms and conditions/i }));
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() => expect(commitSessionCookie).toHaveBeenCalled());
    expect(push).toHaveBeenCalledWith("/projects");
  });

  it("pending email verification navigates to pending page without committing session", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValue({
      status: "PENDING_EMAIL_VERIFICATION",
      login: null,
    });
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret12");
    await user.click(screen.getByRole("checkbox", { name: /privacy policy/i }));
    await user.click(screen.getByRole("checkbox", { name: /terms and conditions/i }));
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() =>
      expect(push).toHaveBeenCalledWith("/register/pending?email=a%40b.com"),
    );
    expect(commitSessionCookie).not.toHaveBeenCalled();
  });

  it("pending verification never commits session even if response incorrectly includes login tokens", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValue({
      status: "PENDING_EMAIL_VERIFICATION",
      login: {
        accessToken: "should-not-use",
        refreshToken: "should-not-use",
        user: { id: "1", email: "a@b.com", name: "N", role: "USER" },
      },
    });
    render(
      <IntlTestProvider>
        <RegisterForm />
      </IntlTestProvider>,
    );
    await user.type(screen.getByLabelText(/display name/i), "N");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret12");
    await user.click(screen.getByRole("checkbox", { name: /privacy policy/i }));
    await user.click(screen.getByRole("checkbox", { name: /terms and conditions/i }));
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() =>
      expect(push).toHaveBeenCalledWith("/register/pending?email=a%40b.com"),
    );
    expect(commitSessionCookie).not.toHaveBeenCalled();
    expect(push).not.toHaveBeenCalledWith("/projects");
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
    await user.type(screen.getByLabelText(/^password$/i), "secret12");
    await user.type(screen.getByLabelText(/repeat password/i), "secret12");
    await user.click(screen.getByRole("checkbox", { name: /privacy policy/i }));
    await user.click(screen.getByRole("checkbox", { name: /terms and conditions/i }));
    await user.click(screen.getByRole("button", { name: /^Register$/i }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });
});
