import { getTranslations } from "next-intl/server";

type PrivacyPolicyPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function PrivacyPolicyPage({ params }: PrivacyPolicyPageProps) {
  const { locale } = await params;
  const t = await getTranslations("PrivacyPolicy");

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">{t("title")}</h1>
      <p className="text-sm text-muted-foreground">
        {t("meta", { version: t("version"), lastUpdated: t("lastUpdated"), locale })}
      </p>
      <p>{t("intro")}</p>
      <p>{t("dataProcessing")}</p>
      <p>{t("userResponsibility")}</p>
      <p>{t("modelServices")}</p>
      <p>{t("dataRetention")}</p>
      <p>{t("security")}</p>
      <p>
        {t("contact")}{" "}
        <a className="underline" href={`mailto:${t("contactEmail")}`}>
          {t("contactEmail")}
        </a>
      </p>
    </main>
  );
}
