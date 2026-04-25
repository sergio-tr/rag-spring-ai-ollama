import { getTranslations } from "next-intl/server";
import { ForgotPasswordView } from "@/features/auth/components/ForgotPasswordView";

export default async function ForgotPasswordPage() {
  const t = await getTranslations("Auth");
  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("forgotPasswordTitle")}</h1>
      <ForgotPasswordView />
    </div>
  );
}

