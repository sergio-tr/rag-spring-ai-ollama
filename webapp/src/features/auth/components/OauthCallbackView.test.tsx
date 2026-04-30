import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { authApiPath } from "@/lib/api-client";

const apiFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...a: unknown[]) => apiFetch(...a),
  authApiPath: (path: string) => `/api/test/auth${path.startsWith("/") ? path : `/${path}`}`,
  ApiError: class ApiError extends Error {},
}));

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

let mockCode = "";
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mockCode ? `code=${encodeURIComponent(mockCode)}` : ""),
}));

import { OauthCallbackView } from "./OauthCallbackView";

describe("OauthCallbackView", () => {
  beforeEach(() => {
    mockCode = "";
    apiFetch.mockReset();
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

  it("exchanges code and redirects on success", async () => {
    mockCode = "c1";
    apiFetch.mockResolvedValueOnce({ accessToken: "a", refreshToken: "r" });

    render(
      <IntlTestProvider>
        <OauthCallbackView provider="google" />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalled();
      expect(apiFetch).toHaveBeenCalledWith(
        authApiPath("/oauth/exchange"),
        expect.objectContaining({
          method: "POST",
        }),
      );
      expect(commitSessionCookie).toHaveBeenCalledWith({ accessToken: "a", refreshToken: "r" });
      expect(replace).toHaveBeenCalledWith("/projects");
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("shows error message on ApiError", async () => {
    mockCode = "c1";
    const { ApiError } = await import("@/lib/api-client");
    apiFetch.mockRejectedValueOnce(new ApiError(500, "nope"));

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
  });
});

