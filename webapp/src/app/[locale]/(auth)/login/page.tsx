import { LoginForm } from "@/features/auth/components/LoginForm";
import { isOAuthGoogleEnabled } from "@/lib/oauth-google-enabled";
import { getTranslations } from "next-intl/server";

export const dynamic = "force-dynamic";

export default async function LoginPage() {
  const t = await getTranslations("Auth");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("loginTitle")}</h1>
      <LoginForm oauthGoogleEnabled={isOAuthGoogleEnabled()} />
    </div>
  );
}
