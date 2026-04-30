"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiFetch, authApiPath } from "@/lib/api-client";
import { Link } from "@/navigation";

const schema = z.object({
  email: z.email(),
});

type Values = z.infer<typeof schema>;

export function ForgotPasswordView() {
  const t = useTranslations("Auth");
  const locale = useLocale();
  const [status, setStatus] = useState<"idle" | "busy" | "done">("idle");
  const [message, setMessage] = useState<string | null>(null);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  });

  async function onSubmit(values: Values) {
    setMessage(null);
    setStatus("busy");
    try {
      await apiFetch(authApiPath("/forgot-password"), {
        method: "POST",
        skipCredentials: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: values.email, locale }),
      });
      setStatus("done");
      setMessage(t("forgotPasswordSubmitted"));
    } catch {
      setStatus("idle");
      setMessage(t("networkError"));
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <div className="flex flex-col gap-2">
          <Label htmlFor="email">{t("email")}</Label>
          <Input id="email" type="email" autoComplete="email" {...form.register("email")} />
          {form.formState.errors.email && (
            <p className="text-destructive text-sm" role="alert">
              {form.formState.errors.email.message}
            </p>
          )}
        </div>
        {message && (
          <p className="text-muted-foreground text-sm" role="status">
            {message}
          </p>
        )}
        <Button type="submit" disabled={status === "busy" || status === "done"}>
          {t("forgotPasswordCta")}
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

