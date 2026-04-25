"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useRouter } from "@/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, apiFetch } from "@/lib/api-client";
import { Link } from "@/navigation";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import { setStoredUserRole } from "@/lib/user-role";
import {
  loginSchema,
  type LoginFormValues,
} from "@/features/auth/schemas/auth-schemas";
import type { LoginResponse } from "@/types/api";

export function LoginForm() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const oauthGoogleEnabled = process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED === "true";

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    setFormError(null);
    try {
      const data = await apiFetch<LoginResponse>("/api/auth/login", {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: values.email,
          password: values.password,
        }),
      });
      await commitSessionCookie({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      });
      setStoredUserRole(data.user.role);
      router.push("/projects");
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setFormError(t("invalidCredentials"));
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
        <Button asChild type="button" variant="secondary">
          <a href="/api/auth/oauth/google/start">{t("oauthGoogleCta")}</a>
        </Button>
      )}
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">{t("email")}</Label>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          {...form.register("email")}
        />
        {form.formState.errors.email && (
          <p className="text-destructive text-sm" role="alert">
            {form.formState.errors.email.message}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <Label htmlFor="password">{t("password")}</Label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          {...form.register("password")}
        />
        {form.formState.errors.password && (
          <p className="text-destructive text-sm" role="alert">
            {form.formState.errors.password.message}
          </p>
        )}
      </div>
      {formError && (
        <p className="text-destructive text-sm" role="alert">
          {formError}
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
