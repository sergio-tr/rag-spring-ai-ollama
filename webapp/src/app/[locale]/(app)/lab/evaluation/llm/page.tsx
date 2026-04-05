"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { useTranslations } from "next-intl";

export default function LabLlmEvalPage() {
  const t = useTranslations("Lab");

  return (
    <LabEvaluationRunCard
      evalBasePath="/lab/evaluations/llm"
      cardTitle={t("llmEvalTitle")}
      cardDescription={t("llmEvalDescription")}
      runButtonTestId="lab-llm-run"
      radioGroupName="follow-llm"
    />
  );
}
