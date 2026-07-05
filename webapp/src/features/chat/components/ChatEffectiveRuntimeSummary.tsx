"use client";

import { useTranslations } from "next-intl";
import { useMeEffectiveRuntime } from "@/features/settings/hooks/use-me-effective-runtime";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import type { MeEffectiveRuntimeResponse } from "@/types/api";

function roleModelId(data: MeEffectiveRuntimeResponse | undefined, roleId: string): string {
  const role = data?.taskRoles?.find((entry) => entry.roleId === roleId);
  return typeof role?.modelId === "string" ? role.modelId : "-";
}

type ChatEffectiveRuntimeSummaryProps = Readonly<{
  projectId?: string;
  conversationId?: string;
}>;

export function ChatEffectiveRuntimeSummary({ projectId, conversationId }: ChatEffectiveRuntimeSummaryProps) {
  const t = useTranslations("Chat");
  const query = useMeEffectiveRuntime(projectId, conversationId);

  if (!projectId || !conversationId) {
    return null;
  }

  if (query.isLoading) {
    return (
      <p className="text-muted-foreground text-xs" data-testid="chat-effective-runtime-loading">
        {t("configLoadingShort")}
      </p>
    );
  }

  if (query.isError || !query.data) {
    return null;
  }

  const data = query.data;

  return (
    <section
      className="rounded-md border bg-muted/20 p-3 text-xs"
      data-testid="chat-effective-runtime-summary"
    >
      <h4 className="text-sm font-medium">{t("chatEffectiveRuntimeSummaryTitle")}</h4>
      <p className="text-muted-foreground mt-1">{t("chatEffectiveRuntimeSummaryDescription")}</p>
      <dl className="mt-3 grid gap-2 sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">{t("chatEffectiveRuntimeFinalAnswerModel")}</dt>
          <dd className="font-mono">{roleModelId(data, "final_answer")}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("chatEffectiveRuntimeClassifier")}</dt>
          <dd className="font-mono">{data.classifierModelId ?? "-"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("chatEffectiveRuntimeSnapshotEmbedding")}</dt>
          <dd className="font-mono">{data.snapshotEmbeddingModelId ?? "-"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("chatEffectiveRuntimeNerModel")}</dt>
          <dd className="font-mono">{roleModelId(data, "ner_extraction")}</dd>
        </div>
        {data.effectivePresetId ?? data.presetId ? (
          <div>
            <dt className="text-muted-foreground">{t("chatEffectiveRuntimePreset")}</dt>
            <dd className="font-mono" data-testid="chat-effective-runtime-preset-id">
              {data.effectivePresetId ?? data.presetId}
            </dd>
            {data.presetName ? (
              <dd className="text-muted-foreground mt-0.5" data-testid="chat-effective-runtime-preset-name">
                {toProductPresetDisplayName(data.presetName)}
                {data.presetSource ? ` · ${data.presetSource}` : ""}
              </dd>
            ) : null}
          </div>
        ) : null}
        {data.retrievalTopK != null ? (
          <div>
            <dt className="text-muted-foreground">{t("chatEffectiveRuntimeTopK")}</dt>
            <dd className="font-mono">{data.retrievalTopK}</dd>
          </div>
        ) : null}
        {data.retrievalSimilarityThreshold != null ? (
          <div>
            <dt className="text-muted-foreground">{t("chatEffectiveRuntimeSimilarity")}</dt>
            <dd className="font-mono">{data.retrievalSimilarityThreshold}</dd>
          </div>
        ) : null}
        {data.materializationStrategy ? (
          <div>
            <dt className="text-muted-foreground">{t("chatEffectiveRuntimeMaterialization")}</dt>
            <dd className="font-mono">{data.materializationStrategy}</dd>
          </div>
        ) : null}
      </dl>
    </section>
  );
}
