"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";

type RagConfigAdvancedJsonPanelProps = Readonly<{
  config: Record<string, unknown> | undefined;
  onApply: (parsed: Record<string, unknown>) => void;
}>;

export function RagConfigAdvancedJsonPanel({ config, onApply }: RagConfigAdvancedJsonPanelProps) {
  const t = useTranslations("Settings");
  const [jsonText, setJsonText] = useState("{}");
  const [jsonError, setJsonError] = useState<string | null>(null);

  useEffect(() => {
    setJsonText(JSON.stringify(config ?? {}, null, 2));
    setJsonError(null);
  }, [config]);

  function applyJson() {
    try {
      const parsed: unknown = JSON.parse(jsonText);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        setJsonError(t("configJsonMustBeObject"));
        return;
      }
      onApply(parsed as Record<string, unknown>);
      setJsonError(null);
    } catch {
      setJsonError(t("configJsonInvalid"));
    }
  }

  return (
    <details className="rounded-md border border-border p-3" data-testid="rag-config-advanced-json">
      <summary className="cursor-pointer text-sm font-medium">{t("configAdvancedJsonSummary")}</summary>
      <p className="text-muted-foreground mt-2 text-xs">{t("configAdvancedJsonHint")}</p>
      <textarea
        aria-label={t("userConfigEditorLabel")}
        className="border-input bg-background mt-3 min-h-40 w-full rounded-md border p-2 font-mono text-xs"
        value={jsonText}
        onChange={(event) => setJsonText(event.target.value)}
      />
      {jsonError ? (
        <p className="text-destructive mt-2 text-sm" role="alert">
          {jsonError}
        </p>
      ) : null}
      <Button type="button" size="sm" className="mt-2" variant="outline" onClick={applyJson}>
        {t("configAdvancedJsonApply")}
      </Button>
    </details>
  );
}
