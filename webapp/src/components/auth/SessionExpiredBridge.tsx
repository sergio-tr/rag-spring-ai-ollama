"use client";

import { useQueryClient } from "@tanstack/react-query";
import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { onApiUnauthorized } from "@/lib/api-client";
import { useLocale } from "next-intl";
import { resetRegisteredClientSessionState } from "@/lib/client-session-reset";
import { hardNavigate } from "@/lib/hard-navigation";
import { useEffect } from "react";

/**
 * Redirects to login when the API returns 401 after refresh fails (long-lived pages).
 * Login/register use `skipCredentials` and do not trigger this path.
 */
export function SessionExpiredBridge() {
  const locale = useLocale();
  const queryClient = useQueryClient();

  useEffect(() => {
    return onApiUnauthorized(() => {
      void clearSessionCookie().then(async () => {
        await resetRegisteredClientSessionState({ queryClient });
        hardNavigate("/login", locale);
      });
    });
  }, [locale, queryClient]);

  return null;
}
