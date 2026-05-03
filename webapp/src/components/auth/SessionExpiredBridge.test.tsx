import { describe, it, expect, vi } from "vitest";
import { render, act, waitFor } from "@testing-library/react";
import { SessionExpiredBridge } from "./SessionExpiredBridge";

const replace = vi.fn();
let unauthorizedCb: (() => void) | undefined;

vi.mock("@/navigation", () => ({
  useRouter: () => ({ replace }),
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
  it("redirects to locale login when unauthorized fires", async () => {
    render(<SessionExpiredBridge />);
    await act(async () => {
      unauthorizedCb?.();
    });
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });
});
