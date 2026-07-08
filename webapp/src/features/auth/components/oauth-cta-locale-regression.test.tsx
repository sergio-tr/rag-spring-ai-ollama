import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";

/**
 * Regression test for the runtime OAuth CTA bug discovered in May 2026.
 *
 * The Google "Continue with Google" CTA must render as a plain anchor
 * pointing to the absolute backend route /api/v5/auth/oauth/google/start.
 * It must NOT use next-intl's <Link> (re-exported as @/navigation Link),
 * because that wrapper rewrites internal hrefs to include the active
 * locale segment (e.g. /en/api/v5/...), which produces an HTTP 404 in
 * the running app.
 *
 * Strategy: mock @/navigation with a Link that mirrors next-intl's
 * runtime behavior (prepend the active locale to absolute internal
 * hrefs). If anyone re-introduces <Link> for the OAuth CTA, the rendered
 * href will pick up /en/ and these assertions will fail.
 */

const ACTIVE_LOCALE = "en";

vi.mock("@/navigation", () => ({
  Link: ({
    children,
    href,
    ...rest
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => {
    const rewritten =
      href.startsWith("/") && !href.startsWith(`/${ACTIVE_LOCALE}/`)
        ? `/${ACTIVE_LOCALE}${href}`
        : href;
    return (
      <a href={rewritten} {...(rest as Record<string, unknown>)}>
        {children}
      </a>
    );
  },
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

vi.mock("@/features/auth/lib/session-client", () => ({
  commitSessionCookie: vi.fn().mockResolvedValue(undefined),
}));

import { LoginForm } from "./LoginForm";
import { RegisterForm } from "./RegisterForm";

function expectGoogleOauthStartHref(href: string) {
  const parsed = href.startsWith("http://") || href.startsWith("https://")
    ? new URL(href)
    : new URL(href, "http://localhost");
  expect(parsed.pathname).toBe("/api/v5/auth/oauth/google/start");
  expect(parsed.searchParams.get("locale")).toBe("en");
  expect(parsed.pathname).not.toMatch(/^\/(en|es|fr|de|pt|it)\//);
}

describe("OAuth CTA locale-prefix regression", () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    vi.stubEnv("NEXT_PUBLIC_RAG_API_PREFIX", "/api/v5");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("LoginForm OAuth CTA href stays /api/v5/auth/oauth/google/start (no locale prefix), even when <Link> would have prepended one", () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <IntlTestProvider locale="en">
          <LoginForm />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const cta = screen.getByTestId("oauth-google-cta");
    expect(cta.tagName).toBe("A");
    const href = cta.getAttribute("href") ?? "";
    expectGoogleOauthStartHref(href);
  });

  it("RegisterForm OAuth CTA href stays /api/v5/auth/oauth/google/start (no locale prefix), even when <Link> would have prepended one", () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <IntlTestProvider locale="en">
          <RegisterForm />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const cta = screen.getByTestId("oauth-google-cta");
    expect(cta.tagName).toBe("A");
    const href = cta.getAttribute("href") ?? "";
    expectGoogleOauthStartHref(href);
  });

  it("regression-mock truth check: <Link> wrapper would prepend /en/ if used directly", async () => {
    // Sanity: prove the mock simulates next-intl's behavior so the assertions
    // above are meaningful. If this fails, the mock above no longer catches
    // the original bug class.
    const { Link } = await import("@/navigation");
    render(<Link href="/api/v5/auth/oauth/google/start">probe</Link>);
    const probe = screen.getByText("probe");
    expect(probe.getAttribute("href")).toBe("/en/api/v5/auth/oauth/google/start");
  });
});
