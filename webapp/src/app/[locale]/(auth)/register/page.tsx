import { RegisterForm } from "@/features/auth/components/RegisterForm";
import { isOAuthGoogleEnabled } from "@/lib/oauth-google-enabled";
import { getTranslations } from "next-intl/server";

export const dynamic = "force-dynamic";

export default async function RegisterPage() {
  const t = await getTranslations("Auth");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("registerTitle")}</h1>
      <RegisterForm oauthGoogleEnabled={isOAuthGoogleEnabled()} />
    </div>
  );
}
