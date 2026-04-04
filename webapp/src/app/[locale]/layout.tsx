import { DocumentLang } from "@/components/providers/document-lang";
import { AppProviders } from "@/components/providers/app-providers";
import { IntlProviderClient } from "@/components/providers/intl-provider";
import { getAppTimeZone } from "@/i18n/timezone";
import { routing } from "@/i18n/routing";
import { hasLocale } from "next-intl";
import { getMessages, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";

type LocaleLayoutProps = {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
};

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }

  setRequestLocale(locale);
  const messages = await getMessages();
  // Sync zone aligned with `getRequestConfig` / NEXT_PUBLIC_TIMEZONE; client provider must receive a
  // concrete string on the first SSR pass (async server NextIntlClientProvider can race in Next 16).
  const timeZone = getAppTimeZone();
  const nowMs = Date.now();

  return (
    <IntlProviderClient locale={locale} messages={messages} timeZone={timeZone} nowMs={nowMs}>
      <AppProviders>
        <DocumentLang />
        {children}
      </AppProviders>
    </IntlProviderClient>
  );
}
