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
import {
  registerSchema,
  type RegisterFormValues,
} from "@/features/auth/schemas/auth-schemas";
import type { LoginResponse } from "@/types/api";

export function RegisterForm() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: "", email: "", password: "" },
  });

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    try {
      const data = await apiFetch<LoginResponse>("/api/auth/register", {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: values.name,
          email: values.email,
          password: values.password,
        }),
      });
      await commitSessionCookie({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      });
      router.push("/projects");
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError && (e.status === 409 || e.status === 400)) {
        setFormError(t("registerFailed"));
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
      <div className="flex flex-col gap-2">
        <Label htmlFor="name">{t("name")}</Label>
        <Input id="name" autoComplete="name" {...form.register("name")} />
        {form.formState.errors.name && (
          <p className="text-destructive text-sm" role="alert">
            {form.formState.errors.name.message}
          </p>
        )}
      </div>
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
          autoComplete="new-password"
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
        {t("submitRegister")}
      </Button>
      <p className="text-muted-foreground text-center text-sm">
        {t("hasAccount")}{" "}
        <Link className="text-primary underline-offset-4 hover:underline" href="/login">
          {t("loginLink")}
        </Link>
      </p>
    </form>
  );
}
