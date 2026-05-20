"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabEmbeddingEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-4">
      <p className="text-muted-foreground text-sm">{t("embeddingBaselineIntro")}</p>
      <LabEvaluationSteps kind="EMBEDDING_RETRIEVAL" />
      <LabEvaluationRunCard
        benchmarkKind="EMBEDDING_RETRIEVAL"
        sectionKey="evaluation-embedding"
        taskTypeHint="EMBEDDING_EVALUATION"
        cardTitle={t("embeddingEvalTitle")}
        cardDescription={t("embeddingEvalDescription")}
        runButtonTestId="lab-embedding-run"
        radioGroupName="follow-embedding"
        introBeforeCard={<p className="text-muted-foreground text-sm">{t("embeddingEvalHelp")}</p>}
      />
    </div>
  );
}
