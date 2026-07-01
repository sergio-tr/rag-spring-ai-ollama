"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { modelRegistryQueryKey, useModelRegistryCheckMutation, useModelRegistryQuery } from "@/features/settings/hooks/use-model-registry";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { pollLabJob } from "@/lib/async-task";
import type { LabJobAcceptedDto, ModelRegistryItemDto } from "@/types/api";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";

function statusLabel(t: (key: string) => string, row: ModelRegistryItemDto, uiPulling: boolean): string {
  if (uiPulling) return t("modelRegistryStatusPulling");
  if (row.status === "AVAILABLE") return t("modelRegistryStatusAvailable");
  if (row.status === "MISSING") return t("modelRegistryStatusMissing");
  return t("modelRegistryStatusError");
}

function ModelRow({
  row,
  pulling,
  onPull,
  onVerify,
  verifyPending,
}: Readonly<{
  row: ModelRegistryItemDto;
  pulling: boolean;
  onPull: (id: string) => void;
  onVerify: (id: string) => void;
  verifyPending: boolean;
}>) {
  const t = useTranslations("Settings");
  const busy = pulling || verifyPending;
  let badgeVariant: "secondary" | "outline" | "destructive" = "destructive";
  if (row.status === "AVAILABLE") {
    badgeVariant = "secondary";
  } else if (row.status === "MISSING") {
    badgeVariant = "outline";
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 py-2 last:border-b-0">
      <div className="min-w-0 flex-1">
        <div className="font-mono text-sm">{row.modelId}</div>
        {row.detail ? <p className="mt-1 text-xs text-muted-foreground">{row.detail}</p> : null}
        {row.embeddingCompatible === false ? (
          <p className="mt-1 text-xs text-destructive">{row.detail ?? t("modelRegistryStatusError")}</p>
        ) : null}
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Badge variant={badgeVariant}>
          {statusLabel(t, row, pulling)}
        </Badge>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid={`model-registry-verify-${row.modelId}`}
          disabled={busy}
          onClick={() => onVerify(row.modelId)}
        >
          {t("modelRegistryVerify")}
        </Button>
        <Button
          type="button"
          size="sm"
          data-testid={`model-registry-pull-${row.modelId}`}
          disabled={busy || row.status === "AVAILABLE"}
          onClick={() => onPull(row.modelId)}
        >
          {t("modelRegistryPull")}
        </Button>
      </div>
    </div>
  );
}

export function ProductModelRegistryCard() {
  const t = useTranslations("Settings");
  const qc = useQueryClient();
  const { data, isLoading, isError, error, refetch } = useModelRegistryQuery();
  const selectableModelsQ = useMeSelectableLlmModels("CHAT");
  const effectiveProvider = selectableModelsQ.data?.effectiveProvider;
  const showModelServerUnreachable =
    Boolean(data && !data.ollamaReachable) && effectiveProvider === "OLLAMA_NATIVE";
  const checkM = useModelRegistryCheckMutation();
  const [pullingId, setPullingId] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const pullM = useMutation({
    mutationFn: async (modelId: string) => {
      const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath("/model-registry/pull"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ modelId }),
      });
      setActionMessage(t("modelRegistryPullQueued"));
      await pollLabJob(accepted.jobId, () => {});
      return modelId;
    },
    onMutate: (modelId) => {
      setPullingId(modelId);
      setActionMessage(null);
    },
    onSuccess: () => {
      setActionMessage(null);
      void qc.invalidateQueries({ queryKey: modelRegistryQueryKey });
    },
    onError: (e) => {
      setActionMessage(e instanceof Error ? e.message : t("modelRegistryPullFailed"));
    },
    onSettled: () => {
      setPullingId(null);
    },
  });

  const onVerify = useCallback(
    (modelId: string) => {
      setActionMessage(null);
      checkM.mutate(
        { modelId, probeEmbedding: true },
        {
          onError: (e) => {
            setActionMessage(e instanceof Error ? e.message : t("modelRegistryCheckFailed"));
          },
        },
      );
    },
    [checkM, t],
  );

  return (
    <Card data-testid="model-registry-card">
      <CardHeader>
        <CardTitle>{t("modelRegistryCardTitle")}</CardTitle>
        <CardDescription>{t("modelRegistryCardDescription")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {isLoading ? <p className="text-sm text-muted-foreground">…</p> : null}
        {isError ? (
          <p className="text-sm text-destructive">
            {error instanceof Error ? error.message : t("modelRegistryLoadFailed")}
            <Button type="button" variant="link" className="ml-2 h-auto p-0" onClick={() => refetch()}>
              {t("modelRegistryRetry")}
            </Button>
          </p>
        ) : null}
        {showModelServerUnreachable ? (
          <p className="text-sm text-destructive" data-testid="model-registry-server-unreachable">
            {t("modelRegistryServerUnreachable")}
            {data?.ollamaErrorMessage ? ` (${data.ollamaErrorMessage})` : ""}
          </p>
        ) : null}
        {actionMessage ? <p className="text-sm text-muted-foreground">{actionMessage}</p> : null}
        {data ? (
          <div className="flex flex-col gap-6">
            <section aria-labelledby="model-registry-llm">
              <h3 id="model-registry-llm" className="mb-2 text-sm font-medium">
                {t("modelRegistrySectionLlm")}
              </h3>
              <div>
                {data.llmModels
                  .filter((row) => row.status === "AVAILABLE")
                  .map((row) => (
                  <ModelRow
                    key={row.modelId}
                    row={row}
                    pulling={pullM.isPending && pullingId === row.modelId}
                    onPull={(id) => pullM.mutate(id)}
                    onVerify={onVerify}
                    verifyPending={
                      checkM.isPending &&
                      checkM.variables?.modelId === row.modelId
                    }
                  />
                ))}
              </div>
            </section>
            <section aria-labelledby="model-registry-emb">
              <h3 id="model-registry-emb" className="mb-2 text-sm font-medium">
                {t("modelRegistrySectionEmbedding")}
              </h3>
              <div>
                {data.embeddingModels
                  .filter((row) => row.status === "AVAILABLE")
                  .map((row) => (
                  <ModelRow
                    key={row.modelId}
                    row={row}
                    pulling={pullM.isPending && pullingId === row.modelId}
                    onPull={(id) => pullM.mutate(id)}
                    onVerify={onVerify}
                    verifyPending={
                      checkM.isPending &&
                      checkM.variables?.modelId === row.modelId
                    }
                  />
                ))}
              </div>
            </section>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
