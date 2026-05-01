"use client";

import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { onApiUnauthorized } from "@/lib/api-client";
import { useRouter } from "@/navigation";
import { useEffect } from "react";

/**
 * Redirects to login when the API returns 401 after refresh fails (long-lived pages).
 * Login/register use `skipCredentials` and do not trigger this path.
 */
export function SessionExpiredBridge() {
  const router = useRouter();

  useEffect(() => {
    return onApiUnauthorized(() => {
      void clearSessionCookie().then(() => {
        // next-intl router applies the active locale; do not pass /{locale}/… again (would yield /en/en/login).
        router.replace("/login");
      });
    });
  }, [router]);

  return null;
}
