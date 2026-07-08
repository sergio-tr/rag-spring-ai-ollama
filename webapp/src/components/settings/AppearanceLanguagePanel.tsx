"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "@/navigation";
import type { AppLocale } from "@/i18n/routing";
import { Button } from "@/components/ui/button";
import { HelpPopover } from "@/features/help/HelpPopover";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  buildPreferencesPutPayload,
  type PersonalizationFormValues,
  type PreferencesFormValues,
} from "@/features/settings/lib/me-canonical-user-config";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { MePersonalizationResponse, MePreferencesResponse } from "@/types/api";

export function AppearanceLanguagePanel({ className }: { className?: string }) {
  const t = useTranslations("Theme");
  const tSettings = useTranslations("Settings");
  const tHelp = useTranslations("Help");
  const { theme, setTheme } = useTheme();
  const locale = useLocale() as AppLocale;
  const router = useRouter();
  const pathname = usePathname();

  const preferencesRef = useRef<Record<string, unknown>>({});
  const personalizationRef = useRef<Record<string, unknown>>({});
  const schemaPrefsRef = useRef<number | null>(null);
  const schemaPersRef = useRef<number | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [preferences, personalization] = await Promise.all([
          apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences")),
          apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization")),
        ]);
        if (cancelled) return;
        preferencesRef.current = { ...preferences.preferences };
        personalizationRef.current = { ...personalization.personalization };
        schemaPrefsRef.current = preferences.schemaVersion;
        schemaPersRef.current = personalization.schemaVersion;
        const storedTheme = personalization.personalization.theme;
        if (storedTheme === "light" || storedTheme === "dark" || storedTheme === "system") {
          setTheme(storedTheme);
        }
      } catch {
        if (!cancelled) {
          setSyncError(tSettings("meLoadError"));
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setTheme, tSettings]);

  const persistLocale = useCallback(
    async (next: AppLocale) => {
      try {
        const values: PreferencesFormValues = { locale: next };
        const payload = buildPreferencesPutPayload(preferencesRef.current, values);
        const res = await apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences"), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ schemaVersion: schemaPrefsRef.current ?? undefined, preferences: payload }),
        });
        preferencesRef.current = { ...res.preferences };
        schemaPrefsRef.current = res.schemaVersion;
        setSyncError(null);
      } catch {
        setSyncError(tSettings("meSaveError"));
      }
    },
    [tSettings],
  );

  const persistTheme = useCallback(
    async (next: PersonalizationFormValues["theme"]) => {
      try {
        const payload = {
          ...personalizationRef.current,
          theme: next,
        };
        const res = await apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization"), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            schemaVersion: schemaPersRef.current ?? undefined,
            personalization: payload,
          }),
        });
        personalizationRef.current = { ...res.personalization };
        schemaPersRef.current = res.schemaVersion;
        setSyncError(null);
      } catch {
        setSyncError(tSettings("meSaveError"));
      }
    },
    [tSettings],
  );

  function switchLocale(next: AppLocale) {
    router.replace(pathname, { locale: next });
    void persistLocale(next);
  }

  function switchTheme(next: PersonalizationFormValues["theme"]) {
    setTheme(next);
    void persistTheme(next);
  }

  return (
    <div className={cn("flex flex-col gap-8", className)} data-testid="appearance-language-panel">
      {syncError ? (
        <p className="text-destructive text-sm" role="alert">
          {syncError}
        </p>
      ) : null}
      <section aria-labelledby="settings-theme-heading">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h2 id="settings-theme-heading" className="font-medium text-sm">
            {tSettings("themeSection")}
          </h2>
          <HelpPopover
            triggerAriaLabel={tHelp("settingsAppearanceTriggerLabel")}
            title={tHelp("settingsAppearanceTitle")}
            message={tHelp("settingsAppearanceMessage")}
            details={tHelp("settingsAppearanceDetails")}
            learnMoreHref="/settings"
            learnMoreLabel={tHelp("settingsAppearanceLearnMore")}
          />
        </div>
        <InlineHelpStatus status="info" label={tHelp("settingsAppearanceInline")} className="mb-3" />
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={theme === "light" ? "default" : "outline"}
            size="sm"
            onClick={() => switchTheme("light")}
          >
            <Sun className="mr-2 size-4" aria-hidden />
            {t("light")}
          </Button>
          <Button
            type="button"
            variant={theme === "dark" ? "default" : "outline"}
            size="sm"
            onClick={() => switchTheme("dark")}
          >
            <Moon className="mr-2 size-4" aria-hidden />
            {t("dark")}
          </Button>
          <Button
            type="button"
            variant={theme === "system" ? "default" : "outline"}
            size="sm"
            onClick={() => switchTheme("system")}
          >
            <Monitor className="mr-2 size-4" aria-hidden />
            {t("system")}
          </Button>
        </div>
      </section>
      <section aria-labelledby="settings-lang-heading">
        <h2 id="settings-lang-heading" className="mb-3 font-medium text-sm">
          {tSettings("languageSection")}
        </h2>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={locale === "en" ? "default" : "outline"}
            size="sm"
            onClick={() => switchLocale("en")}
          >
            English
          </Button>
          <Button
            type="button"
            variant={locale === "es" ? "default" : "outline"}
            size="sm"
            onClick={() => switchLocale("es")}
          >
            Español
          </Button>
        </div>
      </section>
    </div>
  );
}
