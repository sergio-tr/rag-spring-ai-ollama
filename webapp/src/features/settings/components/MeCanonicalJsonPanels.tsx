"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MePersonalizationResponse, MePreferencesResponse } from "@/types/api";

/**
 * Canonical `/me/preferences` and `/me/personalization` JSON stores (validated server-side).
 */
export function MeCanonicalJsonPanels() {
  const t = useTranslations("Settings");
  const [prefsJson, setPrefsJson] = useState("");
  const [persJson, setPersJson] = useState("");
  const [schemaPrefs, setSchemaPrefs] = useState<number | null>(null);
  const [schemaPers, setSchemaPers] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingPrefs, setSavingPrefs] = useState(false);
  const [savingPers, setSavingPers] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setErr(null);
      try {
        const [p, pe] = await Promise.all([
          apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences")),
          apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization")),
        ]);
        if (cancelled) return;
        setPrefsJson(JSON.stringify(p.preferences ?? {}, null, 2));
        setPersJson(JSON.stringify(pe.personalization ?? {}, null, 2));
        setSchemaPrefs(p.schemaVersion);
        setSchemaPers(pe.schemaVersion);
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

  async function savePrefs() {
    setSavingPrefs(true);
    setErr(null);
    try {
      let obj: Record<string, unknown>;
      try {
        obj = JSON.parse(prefsJson) as Record<string, unknown>;
      } catch {
        throw new Error(t("meInvalidJson"));
      }
      const res = await apiFetch<MePreferencesResponse>(apiProductPath("/me/preferences"), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ schemaVersion: schemaPrefs ?? undefined, preferences: obj }),
      });
      setSchemaPrefs(res.schemaVersion);
      setPrefsJson(JSON.stringify(res.preferences ?? {}, null, 2));
    } catch (e) {
      setErr(e instanceof Error ? e.message : t("meSaveError"));
    } finally {
      setSavingPrefs(false);
    }
  }

  async function savePers() {
    setSavingPers(true);
    setErr(null);
    try {
      let obj: Record<string, unknown>;
      try {
        obj = JSON.parse(persJson) as Record<string, unknown>;
      } catch {
        throw new Error(t("meInvalidJson"));
      }
      const res = await apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization"), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ schemaVersion: schemaPers ?? undefined, personalization: obj }),
      });
      setSchemaPers(res.schemaVersion);
      setPersJson(JSON.stringify(res.personalization ?? {}, null, 2));
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
    <div className="flex flex-col gap-8">
      {err && (
        <p className="text-destructive text-sm" role="alert">
          {err}
        </p>
      )}
      <section className="flex flex-col gap-2">
        <h2 className="font-medium text-sm">{t("mePreferencesTitle")}</h2>
        <p className="text-muted-foreground text-xs">{t("mePreferencesDescription")}</p>
        <Label htmlFor="me-prefs-json">{t("mePreferencesLabel")}</Label>
        <Textarea
          id="me-prefs-json"
          className="font-mono text-xs"
          rows={8}
          value={prefsJson}
          onChange={(e) => setPrefsJson(e.target.value)}
        />
        <Button type="button" size="sm" onClick={() => void savePrefs()} disabled={savingPrefs}>
          {t("configSave")}
        </Button>
      </section>
      <section className="flex flex-col gap-2">
        <h2 className="font-medium text-sm">{t("mePersonalizationTitle")}</h2>
        <p className="text-muted-foreground text-xs">{t("mePersonalizationDescription")}</p>
        <Label htmlFor="me-pers-json">{t("mePersonalizationLabel")}</Label>
        <Textarea
          id="me-pers-json"
          className="font-mono text-xs"
          rows={8}
          value={persJson}
          onChange={(e) => setPersJson(e.target.value)}
        />
        <Button type="button" size="sm" onClick={() => void savePers()} disabled={savingPers}>
          {t("configSave")}
        </Button>
      </section>
    </div>
  );
}
