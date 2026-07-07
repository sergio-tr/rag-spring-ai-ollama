"use client";

import { Label } from "@/components/ui/label";
import { RagIndexReadinessMessage } from "@/features/lab/components/rag-index-readiness-message";
import type { RagIndexReadinessDisplay } from "@/features/lab/lib/rag-index-readiness";
import {
  buildRagPresetMaterializationGroups,
  type RagPresetMaterializationGroup,
} from "@/features/lab/lib/rag-preset-run-groups";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import { useTranslations } from "next-intl";

export type LabRagIndexingMaterializationPlanProps = {
  selectedPresetCodes: readonly string[];
  experimentalPresets: readonly ExperimentalPresetCatalogItemDto[] | undefined;
  autoReindex: boolean;
  reuseCompatibleActiveSnapshot: boolean;
  indexReadiness: RagIndexReadinessDisplay | null;
  disabled?: boolean;
  onAutoReindexChange: (value: boolean) => void;
  onReuseCompatibleChange: (value: boolean) => void;
};

function groupLabel(t: (key: string) => string, group: RagPresetMaterializationGroup): string {
  switch (group.groupKey) {
    case "DIRECT_LLM":
      return t("benchmarkRagGroupDirectLlm");
    case "NO_INDEX":
      return t("benchmarkRagGroupNoIndex");
    case "DOCUMENT_LEVEL":
      return t("benchmarkRagGroupDocumentLevel");
    case "CHUNK_LEVEL":
      return t("benchmarkRagGroupChunkLevel");
    case "CHUNK_LEVEL_METADATA":
      return t("benchmarkRagGroupChunkMetadata");
    case "HYBRID_METADATA":
      return t("benchmarkRagGroupHybridMetadata");
    case "MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN":
      return t("benchmarkRagGroupMultiTurnUnsupported");
    default:
      return group.groupKey;
  }
}

/** Preset-derived indexing groups and snapshot rebuild options for RAG evaluation. */
export function LabRagIndexingMaterializationPlan({
  selectedPresetCodes,
  experimentalPresets,
  autoReindex,
  reuseCompatibleActiveSnapshot,
  indexReadiness,
  disabled = false,
  onAutoReindexChange,
  onReuseCompatibleChange,
}: LabRagIndexingMaterializationPlanProps) {
  const t = useTranslations("Lab");
  const groups = buildRagPresetMaterializationGroups(selectedPresetCodes, experimentalPresets);

  return (
    <div
      className="space-y-3 rounded-md border bg-muted/20 p-3"
      data-testid="lab-rag-indexing-materialization-plan"
    >
      <Label className="text-sm">{t("benchmarkIndexingMaterializationPlanTitle")}</Label>
      <p className="text-muted-foreground text-[11px]">{t("benchmarkIndexingMaterializationPlanHint")}</p>

      {groups.length > 0 ? (
        <ul className="space-y-2 text-xs" data-testid="lab-rag-preset-materialization-groups">
          {groups.map((group) => (
            <li key={group.groupKey} className="rounded border px-2 py-1.5" data-testid={`lab-rag-group-${group.groupKey}`}>
              <p className="font-medium">{groupLabel(t, group)}</p>
              <p className="text-muted-foreground mt-0.5">
                {t("benchmarkRagGroupPresets", { presets: group.presetCodes.join(", ") })}
              </p>
              {group.materializationStrategy ? (
                <p className="text-muted-foreground mt-0.5">
                  {t("benchmarkRagGroupMaterialization", { strategy: group.materializationStrategy })}
                </p>
              ) : (
                <p className="text-muted-foreground mt-0.5">{t("benchmarkRagGroupNoMaterialization")}</p>
              )}
              {group.requiresMetadata ? (
                <p className="text-muted-foreground mt-0.5">{t("benchmarkRagGroupMetadataRequired")}</p>
              ) : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-muted-foreground text-xs" data-testid="lab-rag-materialization-empty">
          {t("benchmarkRagMaterializationSelectPresets")}
        </p>
      )}

      <RagIndexReadinessMessage display={indexReadiness} />

      <div className="space-y-2 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            data-testid="lab-rag-auto-reindex"
            checked={autoReindex}
            disabled={disabled}
            onChange={(event) => onAutoReindexChange(event.target.checked)}
          />
          {t("benchmarkRagAutoReindexLabel")}
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            data-testid="lab-rag-reuse-compatible-snapshot"
            checked={reuseCompatibleActiveSnapshot}
            disabled={disabled || !autoReindex}
            onChange={(event) => onReuseCompatibleChange(event.target.checked)}
          />
          {t("benchmarkRagReuseCompatibleSnapshotLabel")}
        </label>
      </div>
    </div>
  );
}
