import { z } from "zod";
import type { AppLocale } from "@/i18n/routing";
import { routing } from "@/i18n/routing";

/**
 * Known keys edited via structured UI for `/me/preferences` and `/me/personalization`.
 * All other keys are treated as "extra": shallow-copied into PUT payloads unchanged (unknown-key preservation).
 */
export const ME_PREFERENCES_STRUCTURED_KEYS = ["locale"] as const;
export const ME_PERSONALIZATION_STRUCTURED_KEYS = ["theme"] as const;

/** Must stay aligned with `routing.locales` (structured preference locale values only). */
const STRUCTURED_LOCALES = routing.locales as unknown as readonly [AppLocale, ...AppLocale[]];

export const preferencesFormSchema = z.object({
  locale: z.enum(STRUCTURED_LOCALES),
});

export const personalizationFormSchema = z.object({
  theme: z.enum(["light", "dark", "system"]),
});

export type PreferencesFormValues = z.infer<typeof preferencesFormSchema>;
export type PersonalizationFormValues = z.infer<typeof personalizationFormSchema>;

export function preferencesFormDefaults(stored: Record<string, unknown>): PreferencesFormValues {
  const raw = stored.locale;
  if (raw === "en" || raw === "es") return { locale: raw };
  return { locale: routing.defaultLocale as AppLocale };
}

export function personalizationFormDefaults(stored: Record<string, unknown>): PersonalizationFormValues {
  const raw = stored.theme;
  if (raw === "light" || raw === "dark" || raw === "system") return { theme: raw };
  return { theme: "system" };
}

export function isStoredPreferenceLocaleUnsupported(stored: Record<string, unknown>): boolean {
  const raw = stored.locale;
  if (raw === undefined) return false;
  return raw !== "en" && raw !== "es";
}

export function isStoredPersonalizationThemeUnsupported(stored: Record<string, unknown>): boolean {
  const raw = stored.theme;
  if (raw === undefined) return false;
  return raw !== "light" && raw !== "dark" && raw !== "system";
}

/** PUT `/me/preferences` body fragment — merges structured locale over the last loaded map (keeps extra keys). */
export function buildPreferencesPutPayload(
  stored: Record<string, unknown>,
  values: PreferencesFormValues,
): Record<string, unknown> {
  return { ...stored, locale: values.locale };
}

/** PUT `/me/personalization` body fragment — merges structured theme over the last loaded map (keeps extra keys). */
export function buildPersonalizationPutPayload(
  stored: Record<string, unknown>,
  values: PersonalizationFormValues,
): Record<string, unknown> {
  return { ...stored, theme: values.theme };
}
