"use client";

import { Link } from "@/navigation";
import { CompactHelp } from "@/features/lab/components/compact-lab-ui";
import type { BenchmarkKind } from "@/types/api";
import { useTranslations } from "next-intl";

export function LabEvaluationSteps({ kind }: Readonly<{ kind: BenchmarkKind }>) {
  const t = useTranslations("Lab");

  const templateKind =
    kind === "LLM_JUDGE_QA"
      ? "llm-model-baseline"
      : kind === "EMBEDDING_RETRIEVAL"
        ? "embedding-baseline"
        : kind === "RAG_PRESET_END_TO_END"
          ? "rag-preset-benchmark"
          : null;

  return (
    <CompactHelp summary={t("compactEvalHelpSummary")} testId="lab-eval-guided-help">
      <ol className="list-decimal space-y-1 pl-5 text-xs text-muted-foreground">
        <li>
          <span className="font-medium text-foreground">{t("guidedStepDatasetTitle")}</span>{" "}
          {t("guidedStepDatasetHint")}
          <div className="mt-1">
            <Link className="text-primary underline-offset-4 hover:underline" href="/lab">
              {t("guidedStepDatasetManageLink")}
            </Link>
            {templateKind ? (
              <span className="text-muted-foreground">
                {" "}
                · {t("guidedStepDatasetTemplateHint", { kind: t(`experimentalDatasetKind.${templateKind}`) })}
              </span>
            ) : null}
          </div>
        </li>
        <li>
          <span className="font-medium text-foreground">{t("guidedStepConfigTitle")}</span>{" "}
          {kind === "LLM_JUDGE_QA"
            ? t("guidedStepConfigLlmHint")
            : kind === "EMBEDDING_RETRIEVAL"
              ? t("guidedStepConfigEmbeddingHint")
              : t("guidedStepConfigRagHint")}
        </li>
        <li>
          <span className="font-medium text-foreground">{t("guidedStepSelectionTitle")}</span>{" "}
          {kind === "RAG_PRESET_END_TO_END" ? t("guidedStepSelectionRagHint") : t("guidedStepSelectionModelsHint")}
        </li>
        <li>
          <span className="font-medium text-foreground">{t("guidedStepRunTitle")}</span> {t("guidedStepRunHint")}
        </li>
        <li>
          <span className="font-medium text-foreground">{t("guidedStepResultsTitle")}</span>{" "}
          {t("guidedStepResultsHint")}
        </li>
        <li>
          <span className="font-medium text-foreground">{t("guidedStepExportTitle")}</span>{" "}
          {t("guidedStepExportHint")}
        </li>
      </ol>
      <p className="text-muted-foreground text-xs">{t("guidedStepAuthHint")}</p>
    </CompactHelp>
  );
}
