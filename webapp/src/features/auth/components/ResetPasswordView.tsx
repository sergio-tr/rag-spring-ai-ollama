"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PasswordVisibilityToggle } from "@/features/auth/components/PasswordVisibilityToggle";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { parseAuthApiErrorCode } from "@/features/auth/lib/parse-auth-api-error-code";
import { Link, useRouter } from "@/navigation";

function schema(t: ReturnType<typeof useTranslations>) {
  return z
    .object({
      password: z.string().min(8, t("passwordTooShort")),
      confirmPassword: z.string().min(1, t("confirmPasswordRequired")),
    })
    .refine((v) => v.password === v.confirmPassword, {
      path: ["confirmPassword"],
      message: t("passwordMismatch"),
    });
}

type Values = { password: string; confirmPassword: string };

function resetErrorMessage(t: ReturnType<typeof useTranslations>, code: string | undefined): string {
  switch (code) {
    case "RESET_TOKEN_EXPIRED":
      return t("resetPasswordTokenExpired");
    case "RESET_TOKEN_ALREADY_USED":
      return t("resetPasswordTokenReused");
    case "RESET_TOKEN_INVALID":
      return t("resetPasswordTokenInvalid");
    case "PASSWORD_RESET_DISABLED":
      return t("resetPasswordDisabled");
    default:
      return t("resetPasswordFailed");
  }
}

export function ResetPasswordView() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const zodSchema = useMemo(() => schema(t), [t]);

  const [status, setStatus] = useState<"idle" | "busy" | "ok" | "error">(() => (token ? "idle" : "error"));
  const [message, setMessage] = useState<string | null>(() => (token ? null : t("resetPasswordMissingToken")));
  /** One toggle controls both password fields (repeat-password UX). */
  const [passwordVisible, setPasswordVisible] = useState(false);

  const form = useForm<Values>({
    resolver: zodResolver(zodSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  useEffect(() => {
    if (status !== "ok") return;
    const id = window.setTimeout(() => router.replace("/login"), 2500);
    return () => window.clearTimeout(id);
  }, [status, router]);

  async function onSubmit(values: Values) {
    if (!token) return;
    setStatus("busy");
    setMessage(null);
    try {
      await apiFetch(authApiPath("/reset-password"), {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword: values.password }),
      });
      setStatus("ok");
      setMessage(t("resetPasswordSuccess"));
    } catch (e) {
      setStatus("error");
      if (e instanceof ApiError) {
        const code = parseAuthApiErrorCode(e);
        setMessage(resetErrorMessage(t, code));
      } else {
        setMessage(t("networkError"));
      }
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <div className="flex flex-col gap-2">
          <Label htmlFor="password">{t("password")}</Label>
          <div className="flex gap-2">
            <Input
              id="password"
              type={passwordVisible ? "text" : "password"}
              autoComplete="new-password"
              className="flex-1"
              {...form.register("password")}
            />
            <PasswordVisibilityToggle
              visible={passwordVisible}
              onToggle={() => setPasswordVisible((prev) => !prev)}
              showPasswordLabel={t("showPassword")}
              hidePasswordLabel={t("hidePassword")}
            />
          </div>
          {form.formState.errors.password && (
            <p className="text-destructive text-sm" role="alert">
              {form.formState.errors.password.message}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="confirmPassword">{t("confirmPassword")}</Label>
          <Input
            id="confirmPassword"
            type={passwordVisible ? "text" : "password"}
            autoComplete="new-password"
            {...form.register("confirmPassword")}
          />
          {form.formState.errors.confirmPassword && (
            <p className="text-destructive text-sm" role="alert">
              {form.formState.errors.confirmPassword.message}
            </p>
          )}
        </div>
        {message && (
          <output
            className={
              status === "error" ? "text-destructive text-sm" : "text-muted-foreground text-sm"
            }
            role={status === "error" ? "alert" : "status"}
          >
            {message}
          </output>
        )}
        <Button type="submit" disabled={status === "busy" || status === "ok" || !token}>
          {t("resetPasswordCta")}
        </Button>
      </form>
      {status === "ok" && (
        <p className="text-center text-sm">
          <Link className="text-primary underline-offset-4 hover:underline" href="/login">
            {t("resetPasswordGoToLogin")}
          </Link>
        </p>
      )}
      <p className="text-muted-foreground text-center text-sm">
        <Link className="text-primary underline-offset-4 hover:underline" href="/login">
          {t("loginLink")}
        </Link>
      </p>
    </div>
  );
}
