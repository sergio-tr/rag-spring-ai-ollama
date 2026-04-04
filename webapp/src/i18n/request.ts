import { getRequestConfig } from "next-intl/server";
import { IntlErrorCode, type IntlError } from "use-intl/core";
import { routing } from "./routing";
import { getAppTimeZone } from "./timezone";

function onIntlError(error: IntlError) {
  if (error.code === IntlErrorCode.ENVIRONMENT_FALLBACK) {
    return;
  }
  console.error(error);
}

export default getRequestConfig(async ({ requestLocale }) => {
  let locale = await requestLocale;
  if (!locale || !routing.locales.includes(locale as "en" | "es")) {
    locale = routing.defaultLocale;
  }

  return {
    locale,
    timeZone: getAppTimeZone(),
    now: new Date(),
    messages: (await import(`../../messages/${locale}.json`)).default,
    onError: onIntlError,
  };
});
