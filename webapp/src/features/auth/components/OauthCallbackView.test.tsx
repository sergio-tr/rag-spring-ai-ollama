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

const commitSessionCookie = vi.fn().mockResolvedValue(undefined);
vi.mock("@/features/auth/lib/session-client", () => ({
  commitSessionCookie: (...a: unknown[]) => commitSessionCookie(...a),
}));

vi.mock("@tanstack/react-query", async (orig) => {
  const mod = await orig<typeof import("@tanstack/react-query")>();
  return {
    ...mod,
    useQueryClient: () => new mod.QueryClient(),
  };
});

const setStoredUserRole = vi.fn();
vi.mock("@/lib/user-role", () => ({
  setStoredUserRole: (...a: unknown[]) => setStoredUserRole(...a),
}));

const resetRegisteredClientSessionState = vi.fn();
vi.mock("@/lib/client-session-reset", async (orig) => {
  const mod = await orig<typeof import("@/lib/client-session-reset")>();
  return {
    ...mod,
    resetRegisteredClientSessionState: (...a: unknown[]) =>
      resetRegisteredClientSessionState(...a),
  };
});

const hardNavigate = vi.fn();
vi.mock("@/lib/hard-navigation", () => ({
  hardNavigate: (...a: unknown[]) => hardNavigate(...a),
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
    sessionStorage.clear();
    vi.mocked(apiFetch).mockReset();
    commitSessionCookie.mockReset();
    commitSessionCookie.mockResolvedValue(undefined);
    resetRegisteredClientSessionState.mockReset();
    setStoredUserRole.mockReset();
    replace.mockReset();
    hardNavigate.mockReset();
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
    vi.mocked(apiFetch).mockResolvedValue({
      accessToken: "a",
      refreshToken: "r",
      user: { id: "u1", email: "u@u.com", name: "U", role: "USER" },
    });

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
    vi.mocked(apiFetch).mockResolvedValue({
      accessToken: "a",
      refreshToken: "r",
      user: { id: "u1", email: "u@test.com", name: "U", role: "ADMIN" },
    });

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(authApiPath("/oauth/exchange"), expect.any(Object));
      expect(resetRegisteredClientSessionState).toHaveBeenCalled();
      expect(commitSessionCookie).toHaveBeenCalledWith({ accessToken: "a", refreshToken: "r" });
      expect(setStoredUserRole).toHaveBeenCalledWith("ADMIN");
      expect(sessionStorage.getItem("rag_last_user_id")).toBe("u1");
      expect(hardNavigate).toHaveBeenCalledWith("/projects", "en");
    });
  });

  it("shows distinct copy on ApiError 404", async () => {
    mockSearch = "code=c1";
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(404, "nope"));

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
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, "bad"));

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
    vi.mocked(apiFetch).mockRejectedValue(new Error("offline"));

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
