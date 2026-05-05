"use client";

import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabLlmEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-4">
      <p className="text-muted-foreground text-sm">{t("llmBaselineIntro")}</p>
      <LabEvaluationSteps kind="LLM_JUDGE_QA" />
      <LabEvaluationRunCard
        benchmarkKind="LLM_JUDGE_QA"
        sectionKey="evaluation-llm"
        taskTypeHint="LLM_EVALUATION"
        cardTitle={t("llmEvalTitle")}
        cardDescription={t("llmEvalDescription")}
        runButtonTestId="lab-llm-run"
        radioGroupName="follow-llm"
      />
    </div>
  );
}
