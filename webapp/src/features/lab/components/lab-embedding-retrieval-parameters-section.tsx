"use client";

import { Label } from "@/components/ui/label";
import { EmbeddingEvaluatorOptionsForm } from "@/features/lab/components/EmbeddingEvaluatorOptionsForm";
import type { LabEmbeddingRuntimeParameters } from "@/features/lab/lib/lab-embedding-hyperparameters";
import type { LabEvaluationModelDto } from "@/types/api";
import { useTranslations } from "next-intl";

export type LabEmbeddingRetrievalParametersSectionProps = {
  value: LabEmbeddingRuntimeParameters;
  onChange: (next: LabEmbeddingRuntimeParameters) => void;
  selectedModels: LabEvaluationModelDto[];
  disabled?: boolean;
  /** RAG runs derive materialization from preset groups — hide the global dropdown. */
  variant: "embedding" | "rag";
};

/** Embedding and retrieval parameters (separate from LLM generation). */
export function LabEmbeddingRetrievalParametersSection({
  value,
  onChange,
  selectedModels,
  disabled = false,
  variant,
}: LabEmbeddingRetrievalParametersSectionProps) {
  const t = useTranslations("Lab");
  const title =
    variant === "rag"
      ? t("benchmarkEmbeddingRetrievalParametersTitle")
      : t("embeddingEvaluationParametersTitle");
  const hint =
    variant === "rag"
      ? t("benchmarkEmbeddingRetrievalParametersHint")
      : t("embeddingEvaluationParametersHint");

  return (
    <div
      className="space-y-3 rounded-md border bg-muted/20 p-3"
      data-testid="lab-embedding-retrieval-parameters-section"
    >
      <Label className="text-sm">{title}</Label>
      <p className="text-muted-foreground text-[11px]">{hint}</p>
      <fieldset disabled={disabled} className="disabled:opacity-60">
        <EmbeddingEvaluatorOptionsForm
          value={value}
          onChange={onChange}
          selectedModels={selectedModels}
          showMaterializationStrategy={variant === "embedding"}
        />
      </fieldset>
      {variant === "rag" ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-rag-explicit-retrieval-hint">
          {t("benchmarkRagExplicitRetrievalHint")}
        </p>
      ) : null}
      {variant === "rag" ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-rag-materialization-derived-hint">
          {t("benchmarkRagMaterializationDerivedHint")}
        </p>
      ) : null}
    </div>
  );
}
