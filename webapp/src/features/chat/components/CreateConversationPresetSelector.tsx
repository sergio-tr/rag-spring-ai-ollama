"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { useProjectCompatiblePresets } from "@/features/chat/hooks/use-project-compatible-presets";
import {
  filterExperimentalPresetsForSelector,
  filterProductPresetsForSelector,
  presetCompatibilityDisabledReason,
  presetProductTierLabel,
  projectCompatiblePresetsEmptyState,
} from "@/features/chat/lib/chat-preset-compatibility";
import { formatChatExperimentalPresetOptionLabel } from "@/lib/product-copy";
import {
  formatProductPresetOptionTitle,
  isDemoBestPresetId,
} from "@/features/chat/lib/preset-latency-tier";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import { createPresetCopyFn } from "@/lib/preset-copy-i18n";
import { resolveIndexAwareDefaultPreset } from "@/features/chat/lib/resolve-index-aware-default-preset";

export type CreateConversationPresetSelectorProps = Readonly<{
  projectId: string;
  value: string;
  onChange: (value: string) => void;
  showIncompatiblePresets: boolean;
  onShowIncompatiblePresetsChange: (show: boolean) => void;
  id?: string;
  selectTestId?: string;
  showIncompatibleTestId?: string;
  enabled?: boolean;
}>;

export function CreateConversationPresetSelector({
  projectId,
  value,
  onChange,
  showIncompatiblePresets,
  onShowIncompatiblePresetsChange,
  id = "new-conv-preset",
  selectTestId = "chat-new-conversation-preset",
  showIncompatibleTestId = "chat-new-conversation-show-incompatible",
  enabled = true,
}: CreateConversationPresetSelectorProps) {
  const t = useTranslations("Chat");
  const tLab = useTranslations("Lab");
  const presetCopyT = createPresetCopyFn(tLab, t);
  const trimmedProjectId = projectId.trim();
  const catalog = useProjectCompatiblePresets(trimmedProjectId || null, { enabled });

  const catalogData = catalog.isSuccess ? catalog.data : undefined;
  const projectIndex = catalogData?.activeSnapshotCapabilities ?? null;
  const isStructuredSearchProject =
    (projectIndex?.materializationStrategy ?? "").trim().toUpperCase() === "STRUCTURED_SEARCH";
  const visibleProductPresets = useMemo(
    () => filterProductPresetsForSelector(catalogData?.productPresets, projectIndex, showIncompatiblePresets),
    [catalogData?.productPresets, projectIndex, showIncompatiblePresets],
  );
  const visibleExperimentalPresets = useMemo(
    () =>
      filterExperimentalPresetsForSelector(
        catalogData?.experimentalPresets,
        projectIndex,
        showIncompatiblePresets,
      ),
    [catalogData?.experimentalPresets, projectIndex, showIncompatiblePresets],
  );
  const emptyState = useMemo(
    () => projectCompatiblePresetsEmptyState(catalogData ?? null, showIncompatiblePresets, projectIndex),
    [catalogData, showIncompatiblePresets, projectIndex],
  );
  const defaultSelection = useMemo(
    () => resolveIndexAwareDefaultPreset(catalogData ?? null),
    [catalogData],
  );
  const selectedValue = useMemo(() => {
    if (value.trim()) {
      const trimmed = value.trim();
      const stillVisible =
        visibleProductPresets.some((item) => item.preset.id === trimmed) ||
        visibleExperimentalPresets.some((item) => item.preset.productPresetId === trimmed);
      return stillVisible ? trimmed : defaultSelection.presetId ?? "";
    }
    return defaultSelection.presetId ?? "";
  }, [value, visibleProductPresets, visibleExperimentalPresets, defaultSelection.presetId]);

  const missingProject = !trimmedProjectId;
  const loadFailed = catalog.isError;
  const selectorDisabled = missingProject || catalog.isLoading || loadFailed || !enabled;
  const showOptions = !missingProject && !loadFailed && catalog.isSuccess;

  return (
    <div className="flex flex-col gap-1">
      <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <Label htmlFor={id} className="shrink-0 text-xs">
          {t("presetInitialLabel")}
        </Label>
        <label className="flex min-w-0 items-center gap-2 text-[11px] text-muted-foreground">
          <input
            type="checkbox"
            data-testid={showIncompatibleTestId}
            checked={showIncompatiblePresets}
            disabled={selectorDisabled}
            onChange={(e) => onShowIncompatiblePresetsChange(e.target.checked)}
          />
          {t("presetShowIncompatible")}
        </label>
      </div>
      {isStructuredSearchProject ? (
        <p
          className="text-muted-foreground text-xs"
          data-testid="chat-new-conversation-preset-structured-search-warning"
        >
          {t("structuredSearchLegacyProjectWarning")}
        </p>
      ) : null}
      <select
        id={id}
        data-testid={selectTestId}
        className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
        value={selectedValue}
        disabled={selectorDisabled}
        onChange={(e) => onChange(e.target.value)}
      >
        {showOptions && !selectedValue ? (
          <option value="" disabled>
            {t("presetNoCompatibleAvailable")}
          </option>
        ) : null}
        {showOptions
          ? visibleProductPresets.map((item) => {
              const reason = presetCompatibilityDisabledReason(item.compatibility);
              const baselineLabel = presetProductTierLabel();
              const label = toProductPresetDisplayName(item.preset.name);
              const display = baselineLabel ? `${label} — ${baselineLabel}` : label;
              const optionTitle = formatProductPresetOptionTitle(item.preset, t);
              return (
                <option
                  key={item.preset.id}
                  value={item.preset.id}
                  disabled={Boolean(reason)}
                  title={reason ?? optionTitle}
                >
                  {display}
                  {item.preset.system ? ` (${t("presetSystem")})` : ""}
                  {reason ? ` (${reason})` : ""}
                </option>
              );
            })
          : null}
        {showOptions
          ? visibleExperimentalPresets.map((item) => {
              const reason = presetCompatibilityDisabledReason(item.compatibility);
              const baselineLabel = presetProductTierLabel();
              const label = formatChatExperimentalPresetOptionLabel(item.preset, presetCopyT);
              const display = baselineLabel ? `${label} — ${baselineLabel}` : label;
              return (
                <option
                  key={item.preset.productPresetId}
                  value={item.preset.productPresetId}
                  disabled={Boolean(reason)}
                  title={reason ?? undefined}
                >
                  {display}
                  {reason ? ` (${reason})` : ""}
                </option>
              );
            })
          : null}
      </select>
      {selectedValue && isDemoBestPresetId(selectedValue) ? (
        <p className="text-muted-foreground text-xs" data-testid="chat-new-conversation-demo-best-description">
          {t("presetDemoBestDescription")}
        </p>
      ) : null}
      {defaultSelection.demoBestIncompatible && defaultSelection.presetId ? (
        <p className="text-muted-foreground text-xs" data-testid="chat-new-conversation-default-preset-hint">
          {t("presetDefaultFallbackHint")}
          {defaultSelection.demoBestDisabledReason ? ` (${defaultSelection.demoBestDisabledReason})` : null}
        </p>
      ) : null}
      {missingProject ? (
        <p className="text-destructive text-xs" role="alert" data-testid="chat-new-conversation-preset-project-error">
          {t("presetCompatibilityProjectRequired")}
        </p>
      ) : null}
      {emptyState === "no-index" ? (
        <p className="text-muted-foreground text-xs" data-testid="chat-new-conversation-preset-empty">
          {t("presetNoIndexEmpty")}
        </p>
      ) : null}
      {emptyState === "no-compatible" ? (
        <p className="text-muted-foreground text-xs" data-testid="chat-new-conversation-preset-empty">
          {t("presetNoCompatibleAvailable")}
        </p>
      ) : null}
      {loadFailed ? (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-destructive text-xs" role="alert" data-testid="chat-new-conversation-preset-load-error">
            {t("presetsLoadError")}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-7 px-2 text-xs"
            data-testid="chat-new-conversation-preset-retry"
            onClick={() => {
              catalog.refetch().catch(() => undefined);
            }}
          >
            {t("presetCompatibilityRetry")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}

export function resolveCreateConversationPresetSelection(
  catalog: ReturnType<typeof useProjectCompatiblePresets>,
  selectedPresetValue: string,
  showIncompatiblePresets: boolean,
) {
  const catalogData = catalog.isSuccess ? catalog.data : undefined;
  const projectIndex = catalogData?.activeSnapshotCapabilities ?? null;
  const visibleProductPresets = filterProductPresetsForSelector(
    catalogData?.productPresets,
    projectIndex,
    showIncompatiblePresets,
  );
  const visibleExperimentalPresets = filterExperimentalPresetsForSelector(
    catalogData?.experimentalPresets,
    projectIndex,
    showIncompatiblePresets,
  );
  const trimmed = selectedPresetValue.trim();
  if (!trimmed) {
    return { selectedValue: "", compatibility: null };
  }
  const stillVisible =
    visibleProductPresets.some((item) => item.preset.id === trimmed) ||
    visibleExperimentalPresets.some((item) => item.preset.productPresetId === trimmed);
  if (!stillVisible) {
    return { selectedValue: "", compatibility: null };
  }
  const productItem = catalogData?.productPresets.find((item) => item.preset.id === trimmed);
  const experimentalItem = catalogData?.experimentalPresets.find(
    (item) => item.preset.productPresetId === trimmed,
  );
  return {
    selectedValue: trimmed,
    compatibility: productItem?.compatibility ?? experimentalItem?.compatibility ?? null,
  };
}
