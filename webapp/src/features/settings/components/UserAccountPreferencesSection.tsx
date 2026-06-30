"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Monitor, Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  buildPersonalizationPutPayload,
  buildPreferencesPutPayload,
  isStoredPreferenceLocaleUnsupported,
  isStoredPersonalizationThemeUnsupported,
  personalizationFormDefaults,
  personalizationFormSchema,
  preferencesFormDefaults,
  preferencesFormSchema,
  type PersonalizationFormValues,
  type PreferencesFormValues,
} from "@/features/settings/lib/me-canonical-user-config";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { MePersonalizationResponse, MePreferencesResponse } from "@/types/api";

/** Language and theme controls for Settings → User (structured, non-JSON). */
export function UserAccountPreferencesSection() {
  const t = useTranslations("Settings");
  const tTheme = useTranslations("Theme");

  const [preferencesMap, setPreferencesMap] = useState<Record<string, unknown>>({});
  const [personalizationMap, setPersonalizationMap] = useState<Record<string, unknown>>({});
  const [schemaPrefs, setSchemaPrefs] = useState<number | null>(null);
  const [schemaPers, setSchemaPers] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingPrefs, setSavingPrefs] = useState(false);
  const [savingPers, setSavingPers] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const prefsForm = useForm<PreferencesFormValues>({
    resolver: zodResolver(preferencesFormSchema),
    defaultValues: preferencesFormDefaults({}),
  });

  const persForm = useForm<PersonalizationFormValues>({
    resolver: zodResolver(personalizationFormSchema),
    defaultValues: personalizationFormDefaults({}),
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setErr(null);
      try {
        const [preferences, personalization] = await Promise.all([
          apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences")),
          apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization")),
        ]);
        if (cancelled) return;
        setPreferencesMap({ ...preferences.preferences });
        setPersonalizationMap({ ...personalization.personalization });
        setSchemaPrefs(preferences.schemaVersion);
        setSchemaPers(personalization.schemaVersion);
      } catch {
        if (!cancelled) setErr(t("meLoadError"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [t]);

  useEffect(() => {
    if (loading) return;
    prefsForm.reset(preferencesFormDefaults(preferencesMap));
  }, [loading, preferencesMap, prefsForm]);

  useEffect(() => {
    if (loading) return;
    persForm.reset(personalizationFormDefaults(personalizationMap));
  }, [loading, personalizationMap, persForm]);

  async function savePrefs(values: PreferencesFormValues) {
    setSavingPrefs(true);
    setErr(null);
    try {
      const payload = buildPreferencesPutPayload(preferencesMap, values);
      const res = await apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences"), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ schemaVersion: schemaPrefs ?? undefined, preferences: payload }),
      });
      setSchemaPrefs(res.schemaVersion);
      const next = { ...res.preferences };
      setPreferencesMap(next);
      prefsForm.reset(preferencesFormDefaults(next));
    } catch (e) {
      setErr(e instanceof Error ? e.message : t("meSaveError"));
    } finally {
      setSavingPrefs(false);
    }
  }

  async function savePers(values: PersonalizationFormValues) {
    setSavingPers(true);
    setErr(null);
    try {
      const payload = buildPersonalizationPutPayload(personalizationMap, values);
      const res = await apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization"), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ schemaVersion: schemaPers ?? undefined, personalization: payload }),
      });
      setSchemaPers(res.schemaVersion);
      const next = { ...res.personalization };
      setPersonalizationMap(next);
      persForm.reset(personalizationFormDefaults(next));
    } catch (e) {
      setErr(e instanceof Error ? e.message : t("meSaveError"));
    } finally {
      setSavingPers(false);
    }
  }

  if (loading) {
    return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
  }

  return (
    <section className="flex flex-col gap-4 border-t pt-4" data-testid="user-account-preferences">
      <h3 className="text-sm font-medium">{t("userPreferencesSectionTitle")}</h3>
      {err ? (
        <p className="text-destructive text-sm" role="alert">
          {err}
        </p>
      ) : null}
      <form className="flex flex-col gap-3" onSubmit={prefsForm.handleSubmit(savePrefs)}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="user-pref-locale">{t("mePreferenceLocaleLabel")}</Label>
          <select
            id="user-pref-locale"
            data-testid="user-pref-locale"
            className={cn(
              "border-input bg-background h-10 w-full max-w-xs rounded-md border px-3 py-2 text-sm",
            )}
            {...prefsForm.register("locale")}
          >
            <option value="en">English</option>
            <option value="es">Español</option>
          </select>
          {isStoredPreferenceLocaleUnsupported(preferencesMap) ? (
            <output className="text-amber-600 text-xs dark:text-amber-500">
              {t("meUnsupportedLocaleWarning", { value: String(preferencesMap.locale ?? "") })}
            </output>
          ) : null}
        </div>
        <Button type="submit" size="sm" variant="outline" disabled={savingPrefs}>
          {t("userPreferencesSaveLanguage")}
        </Button>
      </form>
      <form className="flex flex-col gap-3" onSubmit={persForm.handleSubmit(savePers)}>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">{t("mePersonalizationThemeLabel")}</span>
          <Controller
            name="theme"
            control={persForm.control}
            render={({ field }) => (
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant={field.value === "light" ? "default" : "outline"}
                  size="sm"
                  onClick={() => field.onChange("light")}
                >
                  <Sun className="mr-2 size-4" aria-hidden />
                  {tTheme("light")}
                </Button>
                <Button
                  type="button"
                  variant={field.value === "dark" ? "default" : "outline"}
                  size="sm"
                  onClick={() => field.onChange("dark")}
                >
                  <Moon className="mr-2 size-4" aria-hidden />
                  {tTheme("dark")}
                </Button>
                <Button
                  type="button"
                  variant={field.value === "system" ? "default" : "outline"}
                  size="sm"
                  onClick={() => field.onChange("system")}
                >
                  <Monitor className="mr-2 size-4" aria-hidden />
                  {tTheme("system")}
                </Button>
              </div>
            )}
          />
          {isStoredPersonalizationThemeUnsupported(personalizationMap) ? (
            <output className="text-amber-600 text-xs dark:text-amber-500">
              {t("meUnsupportedThemeWarning", { value: String(personalizationMap.theme ?? "") })}
            </output>
          ) : null}
        </div>
        <Button type="submit" size="sm" variant="outline" disabled={savingPers}>
          {t("userPreferencesSaveTheme")}
        </Button>
      </form>
    </section>
  );
}
