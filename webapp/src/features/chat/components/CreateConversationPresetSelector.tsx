"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { useProjectCompatiblePresets } from "@/features/chat/hooks/use-project-compatible-presets";
import {
  filterCompatibleExperimentalPresets,
  filterCompatibleProductPresets,
  presetCompatibilityDisabledReason,
  projectCompatiblePresetsEmptyState,
} from "@/features/chat/lib/chat-preset-compatibility";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import { createPresetCopyFn } from "@/lib/preset-copy-i18n";
import { formatChatExperimentalPresetOptionLabel } from "@/lib/product-copy";

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
  const visibleProductPresets = useMemo(
    () => filterCompatibleProductPresets(catalogData?.productPresets, showIncompatiblePresets),
    [catalogData?.productPresets, showIncompatiblePresets],
  );
  const visibleExperimentalPresets = useMemo(
    () => filterCompatibleExperimentalPresets(catalogData?.experimentalPresets, showIncompatiblePresets),
    [catalogData?.experimentalPresets, showIncompatiblePresets],
  );
  const emptyState = useMemo(
    () => projectCompatiblePresetsEmptyState(catalogData ?? null, showIncompatiblePresets),
    [catalogData, showIncompatiblePresets],
  );
  const selectedValue = useMemo(() => {
    if (!value) return "";
    const stillVisible =
      visibleProductPresets.some((item) => item.preset.id === value) ||
      visibleExperimentalPresets.some((item) => item.preset.productPresetId === value);
    return stillVisible ? value : "";
  }, [value, visibleProductPresets, visibleExperimentalPresets]);

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
      <select
        id={id}
        data-testid={selectTestId}
        className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
        value={selectedValue}
        disabled={selectorDisabled}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">{t("presetServerDefault")}</option>
        {showOptions
          ? visibleProductPresets.map((item) => {
              const reason = presetCompatibilityDisabledReason(item.compatibility);
              const label = toProductPresetDisplayName(item.preset.name);
              return (
                <option
                  key={item.preset.id}
                  value={item.preset.id}
                  disabled={Boolean(reason)}
                  title={reason ?? undefined}
                >
                  {label}
                  {item.preset.system ? ` (${t("presetSystem")})` : ""}
                  {reason ? ` (${reason})` : ""}
                </option>
              );
            })
          : null}
        {showOptions
          ? visibleExperimentalPresets.map((item) => {
              const reason = presetCompatibilityDisabledReason(item.compatibility);
              const label = formatChatExperimentalPresetOptionLabel(item.preset, presetCopyT);
              return (
                <option
                  key={item.preset.productPresetId}
                  value={item.preset.productPresetId}
                  disabled={Boolean(reason)}
                  title={reason ?? undefined}
                >
                  {label}
                  {reason ? ` (${reason})` : ""}
                </option>
              );
            })
          : null}
      </select>
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
  const visibleProductPresets = filterCompatibleProductPresets(
    catalogData?.productPresets,
    showIncompatiblePresets,
  );
  const visibleExperimentalPresets = filterCompatibleExperimentalPresets(
    catalogData?.experimentalPresets,
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
