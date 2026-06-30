"use client";

import { Button } from "@/components/ui/button";
import type { LlmCatalogModelDto } from "@/types/api";
import { cn } from "@/lib/utils";
import {
  catalogDisplayName,
  catalogRowKey,
  catalogRuntimeStatusLabel,
  catalogRuntimeUnavailableMessage,
  catalogSourceLabel,
  type CatalogAdminLabels,
  isCatalogConfigured,
  isCatalogIndexingDisabled,
  isCatalogRuntimeNotProbed,
  isCatalogRuntimeUnavailable,
  isCatalogVectorIncompatible,
} from "@/features/admin/lib/llm-catalog-admin";
import { productProviderLabel, productProviderLabelsFromAdmin } from "@/lib/product-provider-labels";

type AdminCatalogTranslations = CatalogAdminLabels & {
  catalogEmptySection: string;
  catalogVectorIncompatible: string;
  catalogIndexingDisabledTooltip: string;
  catalogPull: string;
  catalogProvider: string;
  catalogProviderRemoteApi: string;
  catalogProviderLocalServer: string;
  catalogCapability: string;
  catalogModelName: string;
  catalogDisplayName: string;
  catalogConfigured: string;
  catalogConfiguredYes: string;
  catalogConfiguredNo: string;
  catalogSource: string;
  catalogRuntimeStatus: string;
  catalogEmbeddingDimensions: string;
  catalogVectorCompatible: string;
  catalogVectorCompatibleYes: string;
  catalogVectorCompatibleNo: string;
  catalogVectorCompatibleNa: string;
  catalogRuntimeNotProbedNote: string;
  modelAvailable: string;
  modelMissing: string;
};

function CatalogField({
  label,
  value,
  testId,
  mono,
}: Readonly<{
  label: string;
  value: string;
  testId?: string;
  mono?: boolean;
}>) {
  return (
    <div className="min-w-0">
      <dt className="text-muted-foreground">{label}</dt>
      <dd
        className={cn("break-words font-medium [overflow-wrap:anywhere]", mono && "font-mono text-[11px]")}
        data-testid={testId}
      >
        {value}
      </dd>
    </div>
  );
}

export function AdminLlmCatalogSection({
  title,
  rows,
  emptyLabel,
  t,
  busyKey,
  onPull,
}: {
  title: string;
  rows: LlmCatalogModelDto[];
  emptyLabel: string;
  t: AdminCatalogTranslations;
  busyKey: string | null;
  onPull?: (model: LlmCatalogModelDto) => void;
}) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-medium text-sm">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{emptyLabel}</p>
      ) : (
        <ul className="flex flex-col gap-2 text-sm">
          {rows.map((row) => {
            const unavailable = isCatalogRuntimeUnavailable(row);
            const notProbed = isCatalogRuntimeNotProbed(row);
            const incompatible = isCatalogVectorIncompatible(row);
            const indexingDisabled = isCatalogIndexingDisabled(row);
            const configured = isCatalogConfigured(row);
            const rowKey = catalogRowKey(row);
            const vectorCompatibleLabel =
              row.compatibleWithCurrentVectorStore == null
                ? t.catalogVectorCompatibleNa
                : row.compatibleWithCurrentVectorStore
                  ? t.catalogVectorCompatibleYes
                  : t.catalogVectorCompatibleNo;
            return (
              <li
                key={rowKey}
                data-testid={`admin-catalog-row-${row.provider}-${row.capability}-${row.modelName}`}
                data-indexing-disabled={indexingDisabled ? "true" : undefined}
                title={indexingDisabled ? t.catalogIndexingDisabledTooltip : undefined}
                className={cn(
                  "bg-muted/40 flex flex-wrap items-start justify-between gap-2 rounded-md border px-3 py-2",
                  indexingDisabled && "border-destructive/35",
                )}
              >
                <div className="min-w-0 flex-1">
                  <div className="font-medium break-words [overflow-wrap:anywhere]">{catalogDisplayName(row)}</div>
                  <dl className="mt-2 grid grid-cols-1 gap-x-4 gap-y-1 text-xs sm:grid-cols-2">
                    <CatalogField
                      label={t.catalogProvider}
                      value={
                        productProviderLabel(row.provider, productProviderLabelsFromAdmin((key) => t[key])) ??
                        "—"
                      }
                      testId={`admin-catalog-provider-${row.modelName}`}
                    />
                    <CatalogField label={t.catalogCapability} value={row.capability} testId={`admin-catalog-capability-${row.modelName}`} />
                    <CatalogField
                      label={t.catalogModelName}
                      value={row.modelName}
                      testId={`admin-catalog-model-name-${row.modelName}`}
                      mono
                    />
                    <CatalogField
                      label={t.catalogDisplayName}
                      value={catalogDisplayName(row)}
                      testId={`admin-catalog-display-name-${row.modelName}`}
                    />
                    <CatalogField
                      label={t.catalogConfigured}
                      value={configured ? t.catalogConfiguredYes : t.catalogConfiguredNo}
                      testId={`admin-catalog-configured-${row.modelName}`}
                    />
                    <CatalogField
                      label={t.catalogRuntimeStatus}
                      value={catalogRuntimeStatusLabel(row.runtimeStatus, row.provider, t)}
                      testId={`admin-catalog-runtime-status-${row.modelName}`}
                    />
                    {row.embeddingDimensions != null ? (
                      <CatalogField
                        label={t.catalogEmbeddingDimensions}
                        value={String(row.embeddingDimensions)}
                        testId={`admin-catalog-embedding-dims-${row.modelName}`}
                      />
                    ) : null}
                    {row.capability === "EMBEDDING" ? (
                      <CatalogField
                        label={t.catalogVectorCompatible}
                        value={vectorCompatibleLabel}
                        testId={`admin-catalog-vector-compatible-${row.modelName}`}
                      />
                    ) : null}
                    <CatalogField
                      label={t.catalogSource}
                      value={catalogSourceLabel(row.source, t)}
                      testId={`admin-catalog-source-${row.modelName}`}
                    />
                  </dl>
                  {notProbed && !unavailable ? (
                    <p
                      className="text-muted-foreground mt-2 text-xs"
                      data-testid={`admin-catalog-not-probed-${row.modelName}`}
                    >
                      {row.runtimeDetail ?? t.catalogRuntimeNotProbedNote}
                    </p>
                  ) : null}
                  {unavailable ? (
                    <p
                      className="mt-2 text-amber-700 text-xs dark:text-amber-400"
                      data-testid={`admin-catalog-unavailable-${row.modelName}`}
                      role="alert"
                    >
                      {catalogRuntimeUnavailableMessage(row, t)}
                      {row.runtimeDetail ? `: ${row.runtimeDetail}` : ""}
                    </p>
                  ) : null}
                  {incompatible ? (
                    <p
                      className="text-destructive mt-1 text-xs"
                      data-testid={`admin-catalog-incompatible-${row.modelName}`}
                      role="alert"
                    >
                      {t.catalogVectorIncompatible}
                    </p>
                  ) : null}
                </div>
                {onPull && row.provider === "OLLAMA_NATIVE" && unavailable ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={busyKey === rowKey}
                    onClick={() => onPull(row)}
                  >
                    {t.catalogPull}
                  </Button>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
