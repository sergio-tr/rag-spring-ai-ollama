"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { Link } from "@/navigation";
import type { AuthPublicConfig } from "@/types/api";

type PendingDeliveryMode = AuthPublicConfig["mailDeliveryMode"] | null;

function introKey(mode: PendingDeliveryMode): string {
  if (mode === "outbox-only") {
    return "registerPendingIntroOutboxOnly";
  }
  if (mode === "smtp") {
    return "registerPendingIntroSmtp";
  }
  return "registerPendingIntro";
}

function resendSentKey(mode: PendingDeliveryMode): string {
  if (mode === "outbox-only") {
    return "registerPendingResendQueuedOutbox";
  }
  return "registerPendingResendSent";
}

export function RegisterPendingVerification() {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const email = searchParams.get("email")?.trim() ?? "";
  const deliveryFromQuery = searchParams.get("delivery")?.trim().toLowerCase() ?? "";

  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [deliveryMode, setDeliveryMode] = useState<PendingDeliveryMode>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const config = await apiFetch<AuthPublicConfig>(authApiPath("/public-config"), {
          skipCredentials: true,
        });
        if (!cancelled) {
          setDeliveryMode(config.mailDeliveryMode);
        }
      } catch {
        if (!cancelled) {
          setDeliveryMode(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const effectiveDelivery = useMemo<PendingDeliveryMode>(() => {
    if (deliveryFromQuery === "smtp" || deliveryFromQuery === "outbox-only") {
      return deliveryFromQuery;
    }
    return deliveryMode;
  }, [deliveryFromQuery, deliveryMode]);

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
      setMessage(t(resendSentKey(effectiveDelivery)));
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
      <p className="text-muted-foreground text-sm">{t(introKey(effectiveDelivery))}</p>
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
        <output className="text-muted-foreground block text-sm" aria-live="polite">
          {message}
        </output>
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
