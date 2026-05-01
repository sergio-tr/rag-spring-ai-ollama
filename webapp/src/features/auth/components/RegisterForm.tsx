"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, apiFetch, authApiPath } from "@/lib/api-client";
import { commitSessionCookie } from "@/features/auth/lib/session-client";
import { setStoredUserRole } from "@/lib/user-role";
import { AuthEmailPasswordFields } from "@/features/auth/components/AuthEmailPasswordFields";
import {
  createRegisterSchema,
  type RegisterFormValues,
} from "@/features/auth/schemas/auth-schemas";
import { shouldCommitRegisterSessionAfterRegister } from "@/features/auth/lib/register-session-policy";
import type { RegisterResponse } from "@/types/api";

export function RegisterForm() {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const oauthGoogleEnabled = process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED === "true";
  const privacyVersion = process.env.NEXT_PUBLIC_LEGAL_PRIVACY_VERSION ?? "";
  const termsVersion = process.env.NEXT_PUBLIC_LEGAL_TERMS_VERSION ?? "";

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(createRegisterSchema(t)),
    defaultValues: {
      name: "",
      email: "",
      password: "",
      confirmPassword: "",
      acceptedPrivacyPolicy: false,
      acceptedTerms: false,
    },
  });

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    try {
      const data = await apiFetch<RegisterResponse>(authApiPath("/register"), {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: values.name,
          email: values.email,
          password: values.password,
          locale,
          acceptedPrivacyPolicy: values.acceptedPrivacyPolicy,
          acceptedTerms: values.acceptedTerms,
          privacyPolicyVersion: privacyVersion || undefined,
          termsVersion: termsVersion || undefined,
        }),
      });
      if (data.status === "PENDING_EMAIL_VERIFICATION") {
        router.push(`/register/pending?email=${encodeURIComponent(values.email)}`);
        router.refresh();
        return;
      }
      if (!shouldCommitRegisterSessionAfterRegister(data)) {
        router.push(`/register/pending?email=${encodeURIComponent(values.email)}`);
        router.refresh();
        return;
      }
      await commitSessionCookie({
        accessToken: data.login!.accessToken,
        refreshToken: data.login!.refreshToken,
      });
      setStoredUserRole(data.login!.user.role);
      router.push("/projects");
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError) {
        const errors = e.meta?.details?.errors;
        if (Array.isArray(errors)) {
          for (const detail of errors) {
            if (!detail || typeof detail !== "object") continue;
            const field = (detail as { field?: unknown }).field;
            const message = (detail as { message?: unknown }).message;
            if (typeof field === "string" && typeof message === "string") {
              form.setError(field as keyof RegisterFormValues, { message });
            }
          }
        }
      }
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
      {oauthGoogleEnabled && (
        <>
          <Link
            className={buttonVariants({ variant: "secondary" })}
            href={`${authApiPath("/oauth/google/start")}?locale=${encodeURIComponent(locale)}`}
          >
            {t("oauthGoogleCta")}
          </Link>
          <p className="text-muted-foreground text-center text-xs">{t("oauthSeparator")}</p>
        </>
      )}
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
        includeConfirmPassword
      />
      <div className="flex flex-col gap-2 text-sm">
        <label className="flex items-start gap-2">
          <input type="checkbox" {...form.register("acceptedPrivacyPolicy")} />
          <span>
            {t("acceptPrivacyPrefix")}{" "}
            <Link className="underline" href="/privacy-policy">
              {t("privacyPolicyLink")}
            </Link>
          </span>
        </label>
        <label className="flex items-start gap-2">
          <input type="checkbox" {...form.register("acceptedTerms")} />
          <span>
            {t("acceptTermsPrefix")}{" "}
            <Link className="underline" href="/terms">
              {t("termsLink")}
            </Link>
          </span>
        </label>
        {(form.formState.errors.acceptedPrivacyPolicy || form.formState.errors.acceptedTerms) && (
          <p className="text-destructive text-sm" role="alert">
            {form.formState.errors.acceptedPrivacyPolicy?.message ??
              form.formState.errors.acceptedTerms?.message}
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
