"use client";

import { Link } from "@/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">{t("guidedStepsTitle")}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <div className="rounded-md border bg-muted/20 p-3 text-xs text-muted-foreground">
          <p>
            This page requires an authenticated session. If you see a permissions error, sign in again and retry.
          </p>
        </div>
        <ol className="list-decimal space-y-1 pl-5">
          <li>
            <span className="font-medium">{t("guidedStepDatasetTitle")}</span>{" "}
            <span className="text-muted-foreground">{t("guidedStepDatasetHint")}</span>
            <div className="text-muted-foreground mt-1 text-xs">
              <Link className="text-primary underline-offset-4 hover:underline" href="/lab">
                {t("guidedStepDatasetManageLink")}
              </Link>
              {templateKind ? (
                <>
                  {" "}
                  ·{" "}
                  <span>
                    {t("guidedStepDatasetTemplateHint", { kind: t(`experimentalDatasetKind.${templateKind}`) })}
                  </span>
                </>
              ) : null}
            </div>
          </li>
          <li>
            <span className="font-medium">{t("guidedStepConfigTitle")}</span>{" "}
            <span className="text-muted-foreground">
              {kind === "LLM_JUDGE_QA"
                ? t("guidedStepConfigLlmHint")
                : kind === "EMBEDDING_RETRIEVAL"
                  ? t("guidedStepConfigEmbeddingHint")
                  : t("guidedStepConfigRagHint")}
            </span>
          </li>
          <li>
            <span className="font-medium">{t("guidedStepSelectionTitle")}</span>{" "}
            <span className="text-muted-foreground">
              {kind === "RAG_PRESET_END_TO_END" ? t("guidedStepSelectionRagHint") : t("guidedStepSelectionModelsHint")}
            </span>
          </li>
          <li>
            <span className="font-medium">{t("guidedStepRunTitle")}</span>{" "}
            <span className="text-muted-foreground">{t("guidedStepRunHint")}</span>
          </li>
          <li>
            <span className="font-medium">{t("guidedStepResultsTitle")}</span>{" "}
            <span className="text-muted-foreground">{t("guidedStepResultsHint")}</span>
          </li>
          <li>
            <span className="font-medium">{t("guidedStepExportTitle")}</span>{" "}
            <span className="text-muted-foreground">{t("guidedStepExportHint")}</span>
          </li>
        </ol>
      </CardContent>
    </Card>
  );
}

