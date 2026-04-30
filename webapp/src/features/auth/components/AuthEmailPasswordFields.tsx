"use client";

import type { FieldErrors, FieldValues, UseFormRegister } from "react-hook-form";
import { useState } from "react";
import { PasswordVisibilityToggle } from "@/features/auth/components/PasswordVisibilityToggle";
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
            className="flex-1"
            {...register("password" as never)}
          />
          <PasswordVisibilityToggle
            visible={showPassword}
            onToggle={() => setShowPassword((prev) => !prev)}
            showPasswordLabel={t("showPassword")}
            hidePasswordLabel={t("hidePassword")}
          />
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
              className="flex-1"
              {...register("confirmPassword" as never)}
            />
            <PasswordVisibilityToggle
              visible={showConfirmPassword}
              onToggle={() => setShowConfirmPassword((prev) => !prev)}
              showPasswordLabel={t("showRepeatPassword")}
              hidePasswordLabel={t("hideRepeatPassword")}
            />
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

