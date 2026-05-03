import type { UserRole } from "@/types/api";

const STORAGE_KEY = "rag_user_role";

function isUserRole(v: unknown): v is UserRole {
  return v === "USER" || v === "ADMIN";
}

export function getStoredUserRole(): UserRole | null {
  if (globalThis.window === undefined) return null;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return isUserRole(raw) ? raw : null;
  } catch {
    return null;
  }
}

export function setStoredUserRole(role: UserRole | null): void {
  if (globalThis.window === undefined) return;
  try {
    if (role) {
      sessionStorage.setItem(STORAGE_KEY, role);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore quota / private mode
  }
}

