"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { useTranslations } from "next-intl";

export default function LabRagEvalPage() {
  const t = useTranslations("Lab");

  return (
    <LabEvaluationRunCard
      evalBasePath="/lab/evaluations/rag"
      cardTitle={t("ragEvalTitle")}
      cardDescription={t("ragEvalDescription")}
      runButtonTestId="lab-rag-run"
      radioGroupName="follow-rag"
      introBeforeCard={<p className="text-muted-foreground text-sm">{t("ragEvalHelp")}</p>}
    />
  );
}
