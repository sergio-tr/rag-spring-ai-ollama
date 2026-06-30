"use client";

import { useTranslations } from "next-intl";

type RagConfigModelWarningsProps = Readonly<{
  llmModel?: string | null;
  embeddingModel?: string | null;
  llmCatalogIds: string[];
  embeddingCatalogIds: string[];
}>;

export function RagConfigModelWarnings({
  llmModel,
  embeddingModel,
  llmCatalogIds,
  embeddingCatalogIds,
}: RagConfigModelWarningsProps) {
  const t = useTranslations("Settings");
  const warnings: string[] = [];

  const llm = llmModel?.trim() ?? "";
  if (llm && llmCatalogIds.length > 0 && !llmCatalogIds.includes(llm)) {
    warnings.push(t("configSelectedModelUnavailable", { model: llm, kind: t("configModelKindChat") }));
  }

  const embedding = embeddingModel?.trim() ?? "";
  if (embedding && embeddingCatalogIds.length > 0 && !embeddingCatalogIds.includes(embedding)) {
    warnings.push(
      t("configSelectedModelUnavailable", { model: embedding, kind: t("configModelKindEmbedding") }),
    );
  }

  if (warnings.length === 0) {
    return null;
  }

  return (
    <div
      className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-900 dark:text-amber-100"
      data-testid="rag-config-model-warnings"
      role="status"
    >
      <ul className="list-disc space-y-1 pl-4">
        {warnings.map((warning) => (
          <li key={warning}>{warning}</li>
        ))}
      </ul>
    </div>
  );
}
