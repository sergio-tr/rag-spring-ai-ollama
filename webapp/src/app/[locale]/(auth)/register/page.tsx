import { RegisterForm } from "@/features/auth/components/RegisterForm";
import { getTranslations } from "next-intl/server";

export default async function RegisterPage() {
  const t = await getTranslations("Auth");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("registerTitle")}</h1>
      <RegisterForm />
    </div>
  );
}
