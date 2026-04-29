"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { Link, useRouter } from "@/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, apiFetch } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import { setStoredUserRole } from "@/lib/user-role";
import { AuthEmailPasswordFields } from "@/features/auth/components/AuthEmailPasswordFields";
import {
  registerSchema,
  type RegisterFormValues,
} from "@/features/auth/schemas/auth-schemas";
import type { RegisterResponse } from "@/types/api";

export function RegisterForm() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: "", email: "", password: "" },
  });

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    setInfoMessage(null);
    try {
      const data = await apiFetch<RegisterResponse>("/api/auth/register", {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: values.name,
          email: values.email,
          password: values.password,
        }),
      });
      if (data.status === "PENDING_EMAIL_VERIFICATION" || !data.login) {
        setInfoMessage(t("registerVerificationRequired"));
        return;
      }
      await commitSessionCookie({
        accessToken: data.login.accessToken,
        refreshToken: data.login.refreshToken,
      });
      setStoredUserRole(data.login.user.role);
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
      <AuthEmailPasswordFields
        register={form.register}
        errors={form.formState.errors}
        t={t}
        passwordAutoComplete="new-password"
      />
      {infoMessage && (
        <p className="text-muted-foreground text-sm" role="status">
          {infoMessage}
        </p>
      )}
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
