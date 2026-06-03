"use client";

import { RunSummaryCard } from "@/features/lab/components/compact-lab-ui";
import { LabEvaluationRunCard } from "@/features/lab/components/lab-evaluation-run-card";
import { LabEvaluationSteps } from "@/features/lab/components/lab-evaluation-steps";
import { useTranslations } from "next-intl";

export default function LabLlmEvalPage() {
  const t = useTranslations("Lab");

  return (
    <div className="space-y-3" data-testid="lab-llm-eval-page">
      <RunSummaryCard title={t("llmEvalTitle")} summary={t("llmEvalTagline")} />
      <LabEvaluationSteps kind="LLM_JUDGE_QA" />
      <LabEvaluationRunCard
        benchmarkKind="LLM_JUDGE_QA"
        sectionKey="evaluation-llm"
        taskTypeHint="LLM_EVALUATION"
        cardTitle={t("compactSectionRun")}
        runButtonTestId="lab-llm-run"
        radioGroupName="follow-llm"
      />
    </div>
  );
}
