"use client";

import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { onApiUnauthorized } from "@/lib/api-client";
import { useRouter } from "@/navigation";
import { useLocale } from "next-intl";
import { useEffect } from "react";

/**
 * Redirects to login when the API returns 401 after refresh fails (long-lived pages).
 * Login/register use `skipCredentials` and do not trigger this path.
 */
export function SessionExpiredBridge() {
  const router = useRouter();
  const locale = useLocale();

  useEffect(() => {
    return onApiUnauthorized(() => {
      void clearSessionCookie().then(() => {
        router.replace(`/${locale}/login`);
      });
    });
  }, [router, locale]);

  return null;
}
