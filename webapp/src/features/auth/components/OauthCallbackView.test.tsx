import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError } from "@/lib/api-client";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const replace = vi.fn();
const refresh = vi.fn();
const router = { replace, refresh };
vi.mock("@/navigation", () => ({
  useRouter: () => router,
}));

const commitSessionCookie = vi.fn();
vi.mock("@/features/auth/lib/session-client", () => ({
  commitSessionCookie: (...a: unknown[]) => commitSessionCookie(...a),
}));

let mockSearch = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockSearch),
}));

import { apiFetch, authApiPath } from "@/lib/api-client";
import { OauthCallbackView } from "./OauthCallbackView";

describe("OauthCallbackView", () => {
  beforeEach(() => {
    mockSearch = "";
    vi.mocked(apiFetch).mockReset();
    commitSessionCookie.mockReset();
    replace.mockReset();
    refresh.mockReset();
  });

  it("shows missing code message when code is absent", async () => {
    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(apiFetch).not.toHaveBeenCalled();
      expect(replace).not.toHaveBeenCalled();
    });
    expect(screen.getByText(/code/i)).toBeInTheDocument();
  });

  it("still exchanges when state is present alongside code", async () => {
    mockSearch = "code=c1&state=csrf";
    vi.mocked(apiFetch).mockResolvedValueOnce({ accessToken: "a", refreshToken: "r" });

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        authApiPath("/oauth/exchange"),
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ code: "c1" }),
        }),
      );
    });
  });

  it("exchanges code and redirects on success", async () => {
    mockSearch = "code=c1";
    vi.mocked(apiFetch).mockResolvedValueOnce({ accessToken: "a", refreshToken: "r" });

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(authApiPath("/oauth/exchange"), expect.any(Object));
      expect(apiFetch).toHaveBeenCalledWith(
        "/api/v5/auth/oauth/exchange",
        expect.objectContaining({
          method: "POST",
        }),
      );
      expect(commitSessionCookie).toHaveBeenCalledWith({ accessToken: "a", refreshToken: "r" });
      expect(replace).toHaveBeenCalledWith("/projects");
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("shows distinct copy on ApiError 404", async () => {
    mockSearch = "code=c1";
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(404, "nope"));

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalled();
      expect(commitSessionCookie).not.toHaveBeenCalled();
      expect(replace).not.toHaveBeenCalled();
    });
    expect(screen.getByText(/404/i)).toBeInTheDocument();
  });

  it("shows generic OAuth failure on non-404 ApiError", async () => {
    mockSearch = "code=c1";
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, "bad"));

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() =>
      expect(screen.getByText(/OAuth sign-in failed/i)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/404/i)).not.toBeInTheDocument();
  });

  it("shows network error on non-ApiError failure", async () => {
    mockSearch = "code=c1";
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("offline"));

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() =>
      expect(screen.getByText(/network error/i)).toBeInTheDocument(),
    );
  });
});
