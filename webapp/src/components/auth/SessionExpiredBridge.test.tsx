import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { SessionExpiredBridge } from "./SessionExpiredBridge";

let unauthorizedCb: (() => void) | undefined;

const hardNavigate = vi.fn();
vi.mock("@/lib/hard-navigation", () => ({
  hardNavigate: (...args: unknown[]) => hardNavigate(...args),
}));

const resetRegisteredClientSessionState = vi.fn().mockResolvedValue(undefined);
vi.mock("@/lib/client-session-reset", () => ({
  resetRegisteredClientSessionState: (...args: unknown[]) =>
    resetRegisteredClientSessionState(...args),
}));

vi.mock("@/lib/api-client", () => ({
  onApiUnauthorized: (cb: () => void) => {
    unauthorizedCb = cb;
    return () => {
      unauthorizedCb = undefined;
    };
  },
}));

vi.mock("@/features/auth/lib/session-client", () => ({
  clearSessionCookie: vi.fn().mockResolvedValue(undefined),
}));

describe("SessionExpiredBridge", () => {
  beforeEach(() => {
    hardNavigate.mockReset();
    resetRegisteredClientSessionState.mockReset();
  });

  it("redirects to locale login when unauthorized fires", async () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <IntlTestProvider locale="en">
          <SessionExpiredBridge />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await act(async () => {
      unauthorizedCb?.();
    });
    await waitFor(() => expect(hardNavigate).toHaveBeenCalledWith("/login", "en"));
    expect(resetRegisteredClientSessionState).toHaveBeenCalledWith(
      expect.objectContaining({ queryClient }),
    );
  });
});
