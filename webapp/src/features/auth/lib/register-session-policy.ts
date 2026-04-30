import type { RegisterResponse } from "@/types/api";

/**
 * When true, if the backend returns both PENDING_EMAIL_VERIFICATION and a login payload,
 * the webapp commits the session immediately instead of sending the user to the pending screen.
 */
export function shouldCommitRegisterSessionOnPending(): boolean {
  return process.env.NEXT_PUBLIC_REGISTER_COMMIT_SESSION_ON_PENDING === "true";
}

export function shouldCommitRegisterSessionAfterRegister(data: RegisterResponse): boolean {
  if (!data.login) {
    return false;
  }
  if (data.status !== "PENDING_EMAIL_VERIFICATION") {
    return true;
  }
  return shouldCommitRegisterSessionOnPending();
}
