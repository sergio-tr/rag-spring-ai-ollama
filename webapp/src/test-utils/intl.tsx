import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import en from "../../messages/en.json";

type Props = Readonly<{ children: ReactNode; locale?: "en" | "es" }>;

/** Wraps UI with the same locale bundle used in dev (stable assertion strings). */
export function IntlTestProvider({ children, locale = "en" }: Props) {
  return (
    <NextIntlClientProvider locale={locale} messages={en}>
      {children}
    </NextIntlClientProvider>
  );
}
