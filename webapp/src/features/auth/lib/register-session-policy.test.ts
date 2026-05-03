import { describe, it, expect } from "vitest";
import { shouldCommitRegisterSessionAfterRegister } from "./register-session-policy";

describe("shouldCommitRegisterSessionAfterRegister", () => {
  it("returns false for pending verification regardless of login payload", () => {
    expect(
      shouldCommitRegisterSessionAfterRegister({
        status: "PENDING_EMAIL_VERIFICATION",
        login: {
          accessToken: "a",
          refreshToken: "r",
          user: { id: "1", email: "e@e.com", name: "n", role: "USER" },
        },
      }),
    ).toBe(false);
  });

  it("returns false when REGISTERED but login is missing", () => {
    expect(
      shouldCommitRegisterSessionAfterRegister({
        status: "REGISTERED",
        login: null,
      }),
    ).toBe(false);
  });

  it("returns true when REGISTERED with access and refresh tokens", () => {
    expect(
      shouldCommitRegisterSessionAfterRegister({
        status: "REGISTERED",
        login: {
          accessToken: "a",
          refreshToken: "r",
          user: { id: "1", email: "e@e.com", name: "n", role: "USER" },
        },
      }),
    ).toBe(true);
  });
});
