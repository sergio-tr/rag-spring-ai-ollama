import type { RegisterResponse } from "@/types/api";

/**
 * Session is established only after explicit REGISTERED + token payload.
 * Pending email verification never commits cookies (even if a malformed response included tokens).
 */
export function shouldCommitRegisterSessionAfterRegister(data: RegisterResponse): boolean {
  if (data.status !== "REGISTERED") {
    return false;
  }
  const login = data.login;
  return Boolean(
    login?.accessToken &&
      login.accessToken.length > 0 &&
      login?.refreshToken &&
      login.refreshToken.length > 0,
  );
}
