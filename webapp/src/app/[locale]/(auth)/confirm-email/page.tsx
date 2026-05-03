import { getTranslations } from "next-intl/server";
import { ConfirmEmailView } from "@/features/auth/components/ConfirmEmailView";

export default async function ConfirmEmailPage() {
  const t = await getTranslations("Auth");
  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("confirmEmailTitle")}</h1>
      <ConfirmEmailView />
    </div>
  );
}

