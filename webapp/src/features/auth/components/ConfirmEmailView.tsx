"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { Link } from "@/navigation";

export function ConfirmEmailView() {
  const t = useTranslations("Auth");
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const [status, setStatus] = useState<"idle" | "busy" | "ok" | "error">("idle");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      if (!token) {
        setStatus("error");
        setMessage(t("confirmEmailMissingToken"));
        return;
      }
      setStatus("busy");
      setMessage(null);
      try {
        await apiFetch(authApiPath("/confirm-email"), {
          method: "POST",
          skipCredentials: true,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token }),
        });
        if (!cancelled) {
          setStatus("ok");
          setMessage(t("confirmEmailSuccess"));
        }
      } catch (e) {
        if (!cancelled) {
          setStatus("error");
          if (e instanceof ApiError) {
            setMessage(t("confirmEmailFailed"));
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
  }, [token, t]);

  return (
    <div className="flex flex-col gap-4">
      {message && (
        <p
          className={status === "ok" ? "text-sm text-foreground" : "text-destructive text-sm"}
          role={status === "error" ? "alert" : undefined}
        >
          {message}
        </p>
      )}
      <div className="flex gap-3">
        <Link
          className={buttonVariants({})}
          href="/login"
          aria-disabled={status === "busy"}
          tabIndex={status === "busy" ? -1 : 0}
          onClick={(e) => {
            if (status === "busy") e.preventDefault();
          }}
        >
          {t("loginLink")}
        </Link>
      </div>
    </div>
  );
}

