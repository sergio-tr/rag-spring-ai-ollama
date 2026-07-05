"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import { LAST_USER_ID_KEY, resetRegisteredClientSessionState } from "@/lib/client-session-reset";
import { hardNavigate } from "@/lib/hard-navigation";
import { setStoredUserRole } from "@/lib/user-role";
import { AuthEmailPasswordFields } from "@/features/auth/components/AuthEmailPasswordFields";
import { GoogleOAuthButton } from "@/features/auth/components/GoogleOAuthButton";
import {
  createLoginSchema,
  type LoginFormValues,
} from "@/features/auth/schemas/auth-schemas";
import type { LoginResponse } from "@/types/api";

function isEmailNotVerifiedApiError(error: ApiError): boolean {
  if (error.status !== 403) {
    return false;
  }

  const rawBody = error.meta?.rawBodyPreview;
  if (!rawBody) {
    return false;
  }

  try {
    const parsed = JSON.parse(rawBody) as {
      code?: string;
      message?: string;
      error?: { code?: string; message?: string };
    };
    const code = parsed.code ?? parsed.error?.code;
    const message = parsed.message ?? parsed.error?.message ?? "";
    return code === "EMAIL_NOT_VERIFIED" || /email (not verified|verification required)/i.test(message);
  } catch {
    return /EMAIL_NOT_VERIFIED|email (not verified|verification required)/i.test(rawBody);
  }
}

type LoginFormProps = {
  /** Resolved on the server from runtime env; falls back to build-time flag in tests. */
  oauthGoogleEnabled?: boolean;
};

export function LoginForm({ oauthGoogleEnabled: oauthGoogleEnabledProp }: LoginFormProps = {}) {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);
  const oauthGoogleEnabled =
    oauthGoogleEnabledProp ?? process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED === "true";

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(createLoginSchema(t)),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    setFormError(null);
    setUnverifiedEmail(null);
    try {
      const data = await apiFetch<LoginResponse>(authApiPath("/login"), {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: values.email,
          password: values.password,
        }),
      });
      const nextUserId = data.user.id;
      await resetRegisteredClientSessionState({ queryClient });
      await commitSessionCookie({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      });
      sessionStorage.setItem(LAST_USER_ID_KEY, nextUserId);
      setStoredUserRole(data.user.role);
      hardNavigate("/projects", locale);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setFormError(t("invalidCredentials"));
        return;
      }
      if (e instanceof ApiError && e.status === 403 && isEmailNotVerifiedApiError(e)) {
        setUnverifiedEmail(values.email.trim());
        setFormError(t("emailNotVerified"));
        return;
      }
      setFormError(t("networkError"));
    }
  }

  return (
    <form
      className="flex w-full max-w-sm flex-col gap-4"
      onSubmit={form.handleSubmit(onSubmit)}
      noValidate
    >
      {oauthGoogleEnabled && (
        <>
          <GoogleOAuthButton locale={locale} label={t("oauthGoogleCta")} />
          <p className="text-muted-foreground text-center text-xs">{t("oauthSeparator")}</p>
        </>
      )}
      <AuthEmailPasswordFields
        register={form.register}
        errors={form.formState.errors}
        t={t}
        passwordAutoComplete="current-password"
      />
      {formError && (
        <p className="text-destructive text-sm" role="alert">
          {formError}
          {unverifiedEmail ? (
            <>
              {" "}
              <Link
                className="text-primary underline-offset-4 hover:underline"
                href={`/register/pending?email=${encodeURIComponent(unverifiedEmail)}`}
              >
                {t("emailNotVerifiedPendingLink")}
              </Link>
            </>
          ) : null}
        </p>
      )}
      <Button type="submit" disabled={form.formState.isSubmitting}>
        {t("submitLogin")}
      </Button>
      <p className="text-muted-foreground text-center text-sm">
        {t("noAccount")}{" "}
        <Link className="text-primary underline-offset-4 hover:underline" href="/register">
          {t("registerLink")}
        </Link>
      </p>
      <p className="text-muted-foreground text-center text-xs">
        <Link className="text-primary underline-offset-4 hover:underline" href="/forgot-password">
          {t("forgotPasswordLink")}
        </Link>
      </p>
    </form>
  );
}
