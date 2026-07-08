"use client";

import { Link } from "@/navigation";
import { useTranslations } from "next-intl";

/** Explains that RAG evaluation answer generation follows task-level user LLM settings. */
export function LabRagTaskLlmCallout() {
  const t = useTranslations("Lab");

  return (
    <output
      data-testid="lab-rag-task-llm-callout"
      className="block rounded-md border border-sky-500/40 bg-sky-500/10 p-3 text-sky-950 text-sm dark:text-sky-100"
    >
      <p className="font-medium">{t("benchmarkRagTaskLlmCalloutTitle")}</p>
      <p className="text-muted-foreground mt-1 text-xs">{t("benchmarkRagTaskLlmCalloutBody")}</p>
      <p className="mt-2 text-xs">
        <Link
          className="text-primary font-medium underline-offset-4 hover:underline"
          href="/settings/user"
        >
          {t("benchmarkRagTaskLlmSettingsLink")}
        </Link>
      </p>
    </output>
  );
}
