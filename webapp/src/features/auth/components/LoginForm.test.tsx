import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { LAST_USER_ID_KEY } from "@/lib/client-session-reset";

const push = vi.fn();
const refresh = vi.fn();

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
  useRouter: () => ({ push, refresh }),
}));

vi.mock("@/lib/api-client", async (orig) => {
  const mod = await orig<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const commitSessionCookie = vi.fn().mockResolvedValue(undefined);
vi.mock("@/features/auth/lib/session-client", () => ({
  commitSessionCookie: (...a: unknown[]) => commitSessionCookie(...a),
}));

vi.mock("@/lib/client-session-reset", async (orig) => {
  const mod = await orig<typeof import("@/lib/client-session-reset")>();
  return {
    ...mod,
    resetRegisteredClientSessionState: vi.fn().mockResolvedValue(undefined),
  };
});

vi.mock("@/lib/hard-navigation", () => ({
  hardNavigate: vi.fn(),
}));

import { hardNavigate } from "@/lib/hard-navigation";
import { resetRegisteredClientSessionState } from "@/lib/client-session-reset";

vi.mock("@/lib/user-role", () => ({
  setStoredUserRole: vi.fn(),
}));

import { apiFetch } from "@/lib/api-client";
import { LoginForm } from "./LoginForm";

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <IntlTestProvider locale="en">
        <LoginForm />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("LoginForm user-change guard", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(apiFetch).mockReset();
    commitSessionCookie.mockClear();
    vi.mocked(resetRegisteredClientSessionState).mockClear();
    vi.mocked(hardNavigate).mockClear();
    push.mockClear();
    refresh.mockClear();
  });

  it("resets client state on every successful login", async () => {
    sessionStorage.setItem(LAST_USER_ID_KEY, "user-a");
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accessToken: "tok",
      refreshToken: "ref",
      user: { id: "user-b", email: "b@test.com", name: "B", role: "USER" },
    });

    const user = userEvent.setup();
    renderForm();
    await user.type(screen.getByLabelText(/^email$/i), "b@test.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => {
      expect(resetRegisteredClientSessionState).toHaveBeenCalledWith(
        expect.objectContaining({ queryClient: expect.any(QueryClient) }),
      );
      expect(commitSessionCookie).toHaveBeenCalled();
      expect(sessionStorage.getItem(LAST_USER_ID_KEY)).toBe("user-b");
    });
  });

  it("still resets when the same user logs in again", async () => {
    sessionStorage.setItem(LAST_USER_ID_KEY, "user-a");
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accessToken: "tok",
      refreshToken: "ref",
      user: { id: "user-a", email: "a@test.com", name: "A", role: "USER" },
    });

    const user = userEvent.setup();
    renderForm();
    await user.type(screen.getByLabelText(/^email$/i), "a@test.com");
    await user.type(screen.getByLabelText(/^password$/i), "secret");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => expect(commitSessionCookie).toHaveBeenCalled());
    expect(resetRegisteredClientSessionState).toHaveBeenCalled();
  });
});
