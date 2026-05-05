"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabRagEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-4">
      <p className="text-muted-foreground text-sm">{t("ragPresetBenchmarkIntro")}</p>
      <div className="rounded-md border bg-muted/20 p-3 text-sm" data-testid="lab-rag-preset-explainer">
        <p className="font-medium">{t("ragPresetExplainerTitle")}</p>
        <ul className="text-muted-foreground mt-2 list-disc space-y-1 pl-4 text-xs">
          <li>{t("ragPresetExplainerCore")}</li>
          <li>{t("ragPresetExplainerAdvanced")}</li>
          <li>{t("ragPresetExplainerNotSupported")}</li>
        </ul>
      </div>
      <LabEvaluationSteps kind="RAG_PRESET_END_TO_END" />
      <LabEvaluationRunCard
        benchmarkKind="RAG_PRESET_END_TO_END"
        sectionKey="evaluation-rag"
        taskTypeHint="RAG_EVALUATION"
        cardTitle={t("ragEvalTitle")}
        cardDescription={t("ragEvalDescription")}
        runButtonTestId="lab-rag-run"
        radioGroupName="follow-rag"
        introBeforeCard={<p className="text-muted-foreground text-sm">{t("ragEvalHelp")}</p>}
      />
    </div>
  );
}
