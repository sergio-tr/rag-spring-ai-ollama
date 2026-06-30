import { getTranslations } from "next-intl/server";

type TermsPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function TermsPage({ params }: TermsPageProps) {
  const { locale } = await params;
  const t = await getTranslations("Terms");

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">{t("title")}</h1>
      <p className="text-sm text-muted-foreground">
        {t("meta", { version: t("version"), lastUpdated: t("lastUpdated"), locale })}
      </p>
      <p>{t("intro")}</p>
      <p>{t("purpose")}</p>
      <p>{t("userObligations")}</p>
      <p>{t("documentResponsibility")}</p>
      <p>{t("answerDisclaimer")}</p>
      <p>{t("labUse")}</p>
      <p>{t("availability")}</p>
      <p>{t("adminAccess")}</p>
      <p>{t("privacyReference")}</p>
      <p>
        {t("contact")}{" "}
        <a className="underline" href={`mailto:${t("contactEmail")}`}>
          {t("contactEmail")}
        </a>
      </p>
    </main>
  );
}
