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
import { ApiError, apiFetch } from "@/lib/api-client";
import { Link, useRouter } from "@/navigation";

function schema(t: ReturnType<typeof useTranslations>) {
  return z.object({
    password: z.string().min(8, t("passwordTooShort")),
  });
}

type Values = { password: string };

export function ResetPasswordView() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const zodSchema = useMemo(() => schema(t), [t]);

  const [status, setStatus] = useState<"idle" | "busy" | "ok" | "error">("idle");
  const [message, setMessage] = useState<string | null>(null);

  const form = useForm<Values>({
    resolver: zodResolver(zodSchema),
    defaultValues: { password: "" },
  });

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage(t("resetPasswordMissingToken"));
    }
  }, [token, t]);

  async function onSubmit(values: Values) {
    if (!token) return;
    setStatus("busy");
    setMessage(null);
    try {
      await apiFetch("/api/auth/reset-password", {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword: values.password }),
      });
      setStatus("ok");
      setMessage(t("resetPasswordSuccess"));
      router.replace("/login");
    } catch (e) {
      setStatus("error");
      if (e instanceof ApiError) {
        setMessage(t("resetPasswordFailed"));
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
        {message && (
          <p
            className={status === "error" ? "text-destructive text-sm" : "text-muted-foreground text-sm"}
            role={status === "error" ? "alert" : "status"}
          >
            {message}
          </p>
        )}
        <Button type="submit" disabled={status === "busy" || !token}>
          {t("resetPasswordCta")}
        </Button>
      </form>
      <p className="text-muted-foreground text-center text-sm">
        <Link className="text-primary underline-offset-4 hover:underline" href="/login">
          {t("loginLink")}
        </Link>
      </p>
    </div>
  );
}

