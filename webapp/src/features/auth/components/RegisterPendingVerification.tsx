"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { Link } from "@/navigation";

export function RegisterPendingVerification() {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const email = searchParams.get("email")?.trim() ?? "";

  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onResend() {
    if (!email) {
      setError(t("registerPendingMissingEmail"));
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiFetch(authApiPath("/resend-confirmation"), {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, locale }),
      });
      setMessage(t("registerPendingResendSent"));
    } catch (e) {
      if (e instanceof ApiError) {
        setError(t("registerPendingResendFailed"));
      } else {
        setError(t("networkError"));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">{t("registerPendingIntro")}</p>
      {email ? (
        <p className="text-sm">
          <span className="text-muted-foreground">{t("registerPendingEmailLabel")}</span>{" "}
          <span className="font-medium">{email}</span>
        </p>
      ) : (
        <p className="text-destructive text-sm" role="alert">
          {t("registerPendingMissingEmail")}
        </p>
      )}
      {message && (
        <p className="text-muted-foreground text-sm" role="status">
          {message}
        </p>
      )}
      {error && (
        <p className="text-destructive text-sm" role="alert">
          {error}
        </p>
      )}
      <Button type="button" disabled={busy || !email} onClick={() => void onResend()}>
        {t("registerPendingResendCta")}
      </Button>
      <p className="text-muted-foreground text-center text-sm">
        <Link className="text-primary underline-offset-4 hover:underline" href="/login">
          {t("loginLink")}
        </Link>
      </p>
    </div>
  );
}
