import { Suspense } from "react";
import { RegisterPendingVerification } from "@/features/auth/components/RegisterPendingVerification";
import { getTranslations } from "next-intl/server";

export default async function RegisterPendingPage() {
  const t = await getTranslations("Auth");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("registerPendingTitle")}</h1>
      <Suspense fallback={<p className="text-muted-foreground text-sm">{t("registerPendingLoading")}</p>}>
        <RegisterPendingVerification />
      </Suspense>
    </div>
  );
}
