"use client";

import { CompactHelp, RunSummaryCard } from "@/features/lab/components/compact-lab-ui";
import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabRagEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-3" data-testid="lab-rag-eval-page">
      <RunSummaryCard title={t("ragEvalTitle")} summary={t("ragEvalTagline")} />
      <p className="text-muted-foreground text-xs" data-testid="lab-rag-eval-prompt-hint">
        {t("evaluationPromptAssistantConfigurationHint")}
      </p>
      <CompactHelp summary={t("ragPresetHelpSummary")} testId="lab-rag-preset-help">
        <ul className="text-muted-foreground list-disc space-y-1 pl-4 text-xs">
          <li>{t("ragPresetExplainerCore")}</li>
          <li>{t("ragPresetExplainerAdvanced")}</li>
          <li>{t("ragPresetExplainerNotSupported")}</li>
        </ul>
      </CompactHelp>
      <LabEvaluationSteps kind="RAG_PRESET_END_TO_END" />
      <LabEvaluationRunCard
        benchmarkKind="RAG_PRESET_END_TO_END"
        sectionKey="evaluation-rag"
        taskTypeHint="RAG_EVALUATION"
        cardTitle={t("compactSectionRun")}
        runButtonTestId="lab-rag-run"
        radioGroupName="follow-rag"
      />
    </div>
  );
}
