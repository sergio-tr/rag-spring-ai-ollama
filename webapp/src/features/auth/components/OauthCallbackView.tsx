"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRouter } from "@/navigation";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import type { LoginResponse } from "@/types/api";

export function OauthCallbackView({ provider }: { provider: "google" }) {
  const t = useTranslations("Auth");
  const router = useRouter();
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
        await commitSessionCookie({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        });
        if (!cancelled) {
          router.replace("/projects");
          router.refresh();
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
  }, [code, provider, router, t]);

  return <p className="text-muted-foreground text-sm">{message}</p>;
}

