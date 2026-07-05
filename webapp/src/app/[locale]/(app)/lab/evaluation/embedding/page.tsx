"use client";

import { RunSummaryCard } from "@/features/lab/components/compact-lab-ui";
import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabEmbeddingEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-3" data-testid="lab-embedding-eval-page">
      <RunSummaryCard title={t("embeddingEvalTitle")} summary={t("embeddingEvalTagline")} />
      <p className="text-muted-foreground text-xs" data-testid="lab-embedding-eval-hint">
        {t("embeddingEvaluationDeterministicHint")}
      </p>
      <LabEvaluationSteps kind="EMBEDDING_RETRIEVAL" />
      <LabEvaluationRunCard
        benchmarkKind="EMBEDDING_RETRIEVAL"
        sectionKey="evaluation-embedding"
        taskTypeHint="EMBEDDING_EVALUATION"
        cardTitle={t("compactSectionRun")}
        runButtonTestId="lab-embedding-run"
        radioGroupName="follow-embedding"
      />
    </div>
  );
}
