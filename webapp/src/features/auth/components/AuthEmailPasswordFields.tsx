"use client";

import type { FieldErrors, FieldValues, UseFormRegister } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type TFunction = (key: string) => string;

export function AuthEmailPasswordFields<TFieldValues extends FieldValues>(props: {
  register: UseFormRegister<TFieldValues>;
  errors: FieldErrors<TFieldValues>;
  t: TFunction;
  passwordAutoComplete: string;
}) {
  const { register, errors, t, passwordAutoComplete } = props;

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
        <Input
          id="password"
          type="password"
          autoComplete={passwordAutoComplete}
          {...register("password" as never)}
        />
        {errors.password && (
          <p className="text-destructive text-sm" role="alert">
            {(errors.password as { message?: string }).message}
          </p>
        )}
      </div>
    </>
  );
}

