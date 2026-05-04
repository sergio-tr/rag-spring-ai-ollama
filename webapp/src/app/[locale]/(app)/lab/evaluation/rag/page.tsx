"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { useTranslations } from "next-intl";

export default function LabRagEvalPage() {
  const t = useTranslations("Lab");

  return (
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
  );
}
