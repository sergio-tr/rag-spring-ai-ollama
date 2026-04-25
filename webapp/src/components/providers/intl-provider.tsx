"use client";

import type { AbstractIntlMessages } from "next-intl";
import type { ReactNode } from "react";
import { IntlErrorCode, type IntlError } from "use-intl/core";
import { IntlProvider } from "use-intl/react";

type IntlProviderProps = {
  locale: string;
  messages: AbstractIntlMessages;
  /** IANA zone; must be non-empty on the server so `useTranslations` never sees a missing timeZone. */
  timeZone: string;
  /** Unix ms from the server — avoids RSC `Date` serialization edge cases. */
  nowMs: number;
  children: ReactNode;
};

export function onIntlError(error: IntlError) {
  // Belt-and-suspenders: if anything still triggers this (SSR ordering, formatters), avoid crashing logs.
  if (error.code === IntlErrorCode.ENVIRONMENT_FALLBACK) {
    return;
  }
  console.error(error);
}

/**
 * Client-only intl root using `use-intl`'s `IntlProvider` directly (same primitive as `next-intl`'s
 * client `NextIntlClientProvider`) plus an error filter for noisy ENVIRONMENT_FALLBACK in production.
 */
export function IntlProviderClient({
  locale,
  messages,
  timeZone,
  nowMs,
  children,
}: IntlProviderProps) {
  const zone = (timeZone && timeZone.trim()) || "UTC";
  return (
    <IntlProvider
      locale={locale}
      messages={messages}
      timeZone={zone}
      now={new Date(nowMs)}
      onError={onIntlError}
    >
      {children}
    </IntlProvider>
  );
}
