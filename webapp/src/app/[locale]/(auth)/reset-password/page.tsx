import { getTranslations } from "next-intl/server";
import { ResetPasswordView } from "@/features/auth/components/ResetPasswordView";

export default async function ResetPasswordPage() {
  const t = await getTranslations("Auth");
  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("resetPasswordTitle")}</h1>
      <ResetPasswordView />
    </div>
  );
}

