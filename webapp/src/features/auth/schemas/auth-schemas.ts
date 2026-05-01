import { z } from "zod";

type TFunction = (key: string) => string;

export function createLoginSchema(t: TFunction) {
  return z.object({
    email: z.email(t("invalidEmail")),
    // Login must accept the seeded dev password used in CI/local stacks (Flyway seed).
    password: z.string().min(1, t("passwordRequired")),
  });
}

export function createRegisterSchema(t: TFunction) {
  return z
    .object({
      name: z.string().min(1, t("nameRequired")).max(120, t("nameTooLong")),
      email: z.email(t("invalidEmail")),
      password: z.string().min(8, t("passwordTooShort")),
      confirmPassword: z.string().min(1, t("confirmPasswordRequired")),
      acceptedPrivacyPolicy: z
        .boolean()
        .refine((value) => value, { message: t("legalAcceptanceRequired") }),
      acceptedTerms: z
        .boolean()
        .refine((value) => value, { message: t("legalAcceptanceRequired") }),
    })
    .refine((data) => data.password === data.confirmPassword, {
      message: t("passwordMismatch"),
      path: ["confirmPassword"],
    });
}

export const loginSchema = createLoginSchema((k) => k);
export const registerSchema = createRegisterSchema((k) => k);

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;
