import { getTranslations } from "next-intl/server";
import { OauthCallbackView } from "@/features/auth/components/OauthCallbackView";

export default async function OauthGoogleCallbackPage() {
  const t = await getTranslations("Auth");
  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-semibold text-2xl tracking-tight">{t("oauthCallbackTitle")}</h1>
      <OauthCallbackView provider="google" />
    </div>
  );
}

