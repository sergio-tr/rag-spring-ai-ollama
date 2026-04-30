"use client";

import type { FieldErrors, FieldValues, UseFormRegister } from "react-hook-form";
import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type TFunction = (key: string) => string;

export function AuthEmailPasswordFields<TFieldValues extends FieldValues>(props: {
  register: UseFormRegister<TFieldValues>;
  errors: FieldErrors<TFieldValues>;
  t: TFunction;
  passwordAutoComplete: string;
  includeConfirmPassword?: boolean;
}) {
  const { register, errors, t, passwordAutoComplete, includeConfirmPassword } = props;
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  return (
    <>
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">{t("email")}</Label>
        <Input id="email" type="email" autoComplete="email" {...register("email" as never)} />
        {errors.email && (
          <p className="text-destructive text-sm" role="alert">
            {(errors.email as { message?: string }).message}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <Label htmlFor="password">{t("password")}</Label>
        <div className="flex gap-2">
          <Input
            id="password"
            type={showPassword ? "text" : "password"}
            autoComplete={passwordAutoComplete}
            {...register("password" as never)}
          />
          <button
            className="rounded border px-3 text-sm"
            type="button"
            onClick={() => setShowPassword((prev) => !prev)}
          >
            {showPassword ? t("hidePassword") : t("showPassword")}
          </button>
        </div>
        {errors.password && (
          <p className="text-destructive text-sm" role="alert">
            {(errors.password as { message?: string }).message}
          </p>
        )}
      </div>
      {includeConfirmPassword && (
        <div className="flex flex-col gap-2">
          <Label htmlFor="confirmPassword">{t("confirmPassword")}</Label>
          <div className="flex gap-2">
            <Input
              id="confirmPassword"
              type={showConfirmPassword ? "text" : "password"}
              autoComplete="new-password"
              {...register("confirmPassword" as never)}
            />
            <button
              className="rounded border px-3 text-sm"
              type="button"
              onClick={() => setShowConfirmPassword((prev) => !prev)}
            >
              {showConfirmPassword ? t("hidePassword") : t("showPassword")}
            </button>
          </div>
          {errors.confirmPassword && (
            <p className="text-destructive text-sm" role="alert">
              {(errors.confirmPassword as { message?: string }).message}
            </p>
          )}
        </div>
      )}
    </>
  );
}

