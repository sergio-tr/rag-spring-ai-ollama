"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import { LAST_USER_ID_KEY, resetRegisteredClientSessionState } from "@/lib/client-session-reset";
import { hardNavigate } from "@/lib/hard-navigation";
import { setStoredUserRole } from "@/lib/user-role";
import type { LoginResponse } from "@/types/api";

export function OauthCallbackView({ provider }: { provider: "google" }) {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const code = searchParams.get("code") ?? "";

  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      setMessage(code ? t("oauthCallbackWorking") : t("oauthCallbackMissingCode"));
      if (!code) return;
      try {
        const data = await apiFetch<LoginResponse>(authApiPath("/oauth/exchange"), {
          method: "POST",
          skipCredentials: true,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code }),
        });
        const nextUserId = data.user.id;
        await resetRegisteredClientSessionState({ queryClient });
        await commitSessionCookie({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        });
        sessionStorage.setItem(LAST_USER_ID_KEY, nextUserId);
        setStoredUserRole(data.user.role);
        if (!cancelled) {
          hardNavigate("/projects", locale);
        }
      } catch (e) {
        if (!cancelled) {
          if (e instanceof ApiError) {
            setMessage(
              e.status === 404 ? t("oauthCallbackFailedNotFound") : t("oauthCallbackFailed"),
            );
          } else {
            setMessage(t("networkError"));
          }
        }
      }
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [code, locale, provider, queryClient, t]);

  return <p className="text-muted-foreground text-sm">{message}</p>;
}

