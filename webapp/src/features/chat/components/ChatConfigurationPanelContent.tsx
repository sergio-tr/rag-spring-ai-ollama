"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { formatChatExperimentalPresetOptionLabel, formatPresetSupportMessage } from "@/lib/product-copy";
import {
  formatChatPresetTechnicalTitle,
  resolvePresetDisplayName,
  sortPresetsByRank,
} from "@/features/presets/lib/preset-display";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { useClassifierModelsQuery } from "@/features/lab/hooks/use-classifier-registry";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { useActiveProjectSnapshot } from "@/features/projects/hooks/use-active-project-snapshot";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import {
  CompactSummaryRow,
} from "@/features/chat/components/chat-config-compact-ui";
import { chatFailureHintForCode, normalizeChatFailureCode } from "@/features/chat/lib/chat-job-errors";
import { formatRuntimeValidationIssueMessage } from "@/features/chat/lib/runtime-validation-copy";
import { cn } from "@/lib/utils";
import { productProviderLabel, productProviderLabelsFromSettings } from "@/lib/product-provider-labels";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import {
  ANSWER_QUALITY_FEATURE_KEY,
  chatRuntimeLabelKey,
  CLARIFICATION_FEATURE_KEY,
  MEMORY_FEATURE_KEY,
  RETRIEVAL_SETTING_KEYS,
} from "@/features/chat/lib/assistant-config-product-labels";
import { ConfigScopeBadge, resolveFieldScope, type AssistantConfigScope } from "@/features/chat/lib/assistant-config-scope";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

function Section({
  title,
  children,
}: Readonly<{
  title: string;
  children: React.ReactNode;
}>) {
  return (
    <section className="space-y-3" aria-label={title}>
      <h3 className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">{title}</h3>
      {children}
    </section>
  );
}

function Box({ children }: Readonly<{ children: React.ReactNode }>) {
  return <div className="min-w-0 max-w-full rounded-lg border bg-background/60 p-3">{children}</div>;
}

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block min-w-0 max-w-full break-words text-xs font-normal leading-snug [overflow-wrap:anywhere]">
      {children}
    </output>
  );
}

function DisabledReason({
  reason,
  id,
  label,
}: Readonly<{
  reason: string;
  id: string;
  label: string;
}>) {
  return (
    <Popover>
      <PopoverTrigger
        aria-describedby={id}
        className="text-muted-foreground hover:text-foreground inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium"
        type="button"
      >
        {label}
      </PopoverTrigger>
      <PopoverContent id={id} className="w-80 text-xs">
        <p className="text-muted-foreground text-xs leading-relaxed">{reason}</p>
      </PopoverContent>
    </Popover>
  );
}

type EffectiveConfigMap = Record<string, unknown>;

function actionableIssueCode(issue: RuntimeConfigValidationIssueDto): string | null {
  const normalized = normalizeChatFailureCode(issue.code);
  return normalized ?? (issue.code ? issue.code : null);
}

/**
 * Shared content for both the desktop side panel and the mobile drawer.
 *
 * Order is intentionally fixed (user requirement):
 * 1) Document scope
 * 2) Index/project capabilities
 * 3) Model configuration + configuration profile
 * 4) Runtime configuration
 */
export function ChatConfigurationPanelContent() {
  const t = useTranslations("SectionActions");
  const tChat = useTranslations("Chat");
  const tSettings = useTranslations("Settings");
  const tLab = useTranslations("Lab");
  const notLoadedLabel = tChat("configNotLoaded");
  const presetCopyT = (key: string) => {
    if (key.startsWith("chat") || key.startsWith("presetDisplay")) return tChat(key);
    return tLab(key);
  };
  const api = useChatToolbarStore((s) => s.api);

  const modelSelectId = useId();
  const classifierSelectId = useId();
  const presetSelectId = useId();
  const uploadInputRef = useRef<HTMLInputElement>(null);

  const [editOpen, setEditOpen] = useState(false);
  const [runtimeOpen, setRuntimeOpen] = useState(false);
  const [advancedError, setAdvancedError] = useState<string | null>(null);
  const [advancedValidationText, setAdvancedValidationText] = useState<string | null>(null);

  const capabilitiesQuery = useRuntimeConfigCapabilities();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;
  const hasCustomOverride = Boolean(api?.runtimeState?.isCustom);

  const effectiveConfig = api?.runtimeState?.effectiveConfig ?? null;
  const activeLlmModel =
    api?.llmModelChoice?.trim() ||
    api?.runtimeState?.conversationLlmModel?.trim() ||
    "";
  const indexProfileQuery = useProjectIndexProfile(api?.projectId);
  const activeSnapQuery = useActiveProjectSnapshot(api?.projectId);

  const effectiveLoading = Boolean(api?.runtimeStateLoading);
  const effectiveError = api?.runtimeStateError ?? null;
  const patchPending = Boolean(api?.patchConvPending);

  const selectedLlmModel = api?.llmModelChoice?.trim() ?? "";
  const selectableModelNames = useMemo(
    () => new Set((api?.selectableLlmModels ?? []).map((m) => m.modelName)),
    [api?.selectableLlmModels],
  );
  const llmSelectionInvalid = useMemo(() => {
    if (api?.selectableLlmModelsLoading || api?.modelsError) return false;
    if (!selectedLlmModel) return false;
    return !selectableModelNames.has(selectedLlmModel);
  }, [api?.selectableLlmModelsLoading, api?.modelsError, selectedLlmModel, selectableModelNames]);

  const caps = useMemo(() => capabilitiesQuery.data?.capabilities ?? [], [capabilitiesQuery.data?.capabilities]);
  const capByKey = useMemo(() => new Map(caps.map((c) => [c.key, c])), [caps]);
  const blockingIssues = useMemo(
    () =>
      api?.runtimeState?.blockingIssues ??
      api?.runtimeState?.runtimeCompatibility?.blockingIssues ??
      api?.runtimeState?.validation?.errors ??
      [],
    [
      api?.runtimeState?.blockingIssues,
      api?.runtimeState?.runtimeCompatibility?.blockingIssues,
      api?.runtimeState?.validation?.errors,
    ],
  );
  const warningIssues = useMemo(
    () =>
      api?.runtimeState?.warnings ??
      api?.runtimeState?.runtimeCompatibility?.warnings ??
      api?.runtimeState?.validation?.warnings ??
      [],
    [
      api?.runtimeState?.warnings,
      api?.runtimeState?.runtimeCompatibility?.warnings,
      api?.runtimeState?.validation?.warnings,
    ],
  );
  const activeSnapshotCapabilities = api?.runtimeState?.indexCompatibility?.activeSnapshotCapabilities ?? null;
  const documentCounts = useMemo(() => {
    const docs = api?.documents ?? [];
    return {
      total: docs.length,
      ready: docs.filter((d) => d.status === "READY").length,
      ingesting: docs.filter((d) => d.status === "INGESTING").length,
      error: docs.filter((d) => d.status === "ERROR").length,
    };
  }, [api?.documents]);
  const issueByField = useMemo(() => {
    const m = new Map<string, RuntimeConfigValidationIssueDto>();
    for (const issue of blockingIssues) {
      if (issue.field && !m.has(issue.field)) m.set(issue.field, issue);
    }
    return m;
  }, [blockingIssues]);
  const disabledRuntimeFeatureByKey = useMemo(() => {
    const m = new Map<string, string>();
    for (const item of api?.runtimeState?.disabledRuntimeFeatures ?? []) {
      if (item.key) m.set(item.key, item.reason);
    }
    return m;
  }, [api?.runtimeState?.disabledRuntimeFeatures]);

  const classifierModelsQuery = useClassifierModelsQuery(Boolean(api?.projectId));

  const runtimeToggles = useMemo(() => {
    const filtered = caps.filter(
      (c) =>
        (c.category === "RUNTIME_HOT_SWAPPABLE" || c.category === "ADVANCED_RUNTIME") &&
        c.visibleInChat === true &&
        c.configurableInChat === true,
    );
    filtered.sort((a, b) => a.displayOrder - b.displayOrder);
    return filtered;
  }, [caps]);

  const retrievalRuntimeToggles = useMemo(
    () => runtimeToggles.filter((c) => RETRIEVAL_SETTING_KEYS.has(c.key)),
    [runtimeToggles],
  );

  const advancedRuntimeToggles = useMemo(
    () =>
      runtimeToggles.filter(
        (c) =>
          !RETRIEVAL_SETTING_KEYS.has(c.key) &&
          c.key !== MEMORY_FEATURE_KEY &&
          c.key !== CLARIFICATION_FEATURE_KEY &&
          c.key !== ANSWER_QUALITY_FEATURE_KEY,
      ),
    [runtimeToggles],
  );

  const memoryRuntimeCap = useMemo(
    () => runtimeToggles.find((c) => c.key === MEMORY_FEATURE_KEY) ?? capByKey.get(MEMORY_FEATURE_KEY),
    [runtimeToggles, capByKey],
  );

  const clarificationRuntimeCap = useMemo(
    () => runtimeToggles.find((c) => c.key === CLARIFICATION_FEATURE_KEY) ?? capByKey.get(CLARIFICATION_FEATURE_KEY),
    [runtimeToggles, capByKey],
  );

  const answerQualityRuntimeCap = useMemo(
    () => runtimeToggles.find((c) => c.key === ANSWER_QUALITY_FEATURE_KEY) ?? capByKey.get(ANSWER_QUALITY_FEATURE_KEY),
    [runtimeToggles, capByKey],
  );

  const manualOverrideKeySet = useMemo(
    () => new Set(api?.runtimeState?.manualOverrideKeys ?? []),
    [api?.runtimeState?.manualOverrideKeys],
  );

  function scopeLabel(scope: AssistantConfigScope): string {
    if (scope === "conversation") return tChat("configScopeConversationSetting");
    if (scope === "account") return tChat("configScopeAccountDefault");
    return tChat("configScopeProjectSetting");
  }

  function scopeForRuntimeKey(key: string): AssistantConfigScope {
    return resolveFieldScope(key, api?.runtimeState?.manualOverrideKeys ?? [], {
      conversationModelKey: api?.runtimeState?.conversationLlmModel,
      conversationClassifierKey: api?.runtimeState?.conversationClassifierModelId,
    });
  }

  const indexBoundCaps = useMemo(() => {
    const filtered = caps.filter((c) => c.category === "INDEX_BOUND" && c.visibleInChat === true);
    filtered.sort((a, b) => a.displayOrder - b.displayOrder);
    return filtered;
  }, [caps]);

  const embeddingIndexBoundCap = useMemo(
    () => indexBoundCaps.find((c) => c.key === "embeddingModel"),
    [indexBoundCaps],
  );

  const indexBoundCapsWithoutEmbedding = useMemo(
    () => indexBoundCaps.filter((c) => c.key !== "embeddingModel"),
    [indexBoundCaps],
  );

  function formatLlmProviderLabel(provider: string | undefined): string | null {
    return productProviderLabel(provider, productProviderLabelsFromSettings((key) => tSettings(key)));
  }

  const mergedRuntimeFlagValues = useMemo(() => {
    return effectiveConfig && typeof effectiveConfig === "object"
      ? (effectiveConfig as Record<string, unknown>)
      : ({} as Record<string, unknown>);
  }, [effectiveConfig]);

  function coerceBool(v: unknown): boolean {
    return v === true || v === "true";
  }

  const disabledReason = (key: string): string | null => {
    const backendReason = disabledRuntimeFeatureByKey.get(key);
    if (backendReason) return backendReason;
    const issue = issueByField.get(key);
    if (issue?.message) {
      return formatRuntimeValidationIssueMessage(issue, tChat);
    }
    const c = capByKey.get(key);
    if (!c) return null;
    if (c.reasonIfDisabled) return c.reasonIfDisabled;
    if (!c.configurableInChat) return "Not configurable in Chat.";
    if (!c.implemented || !c.engineWired) return c.reasonIfNotImplemented ?? "Not implemented.";
    if (c.requires?.length) {
      for (const reqKey of c.requires) {
        if (!coerceBool(mergedRuntimeFlagValues[reqKey])) {
          return `Requires ${reqKey}=true (e.g. turn on “Use retrieval” first).`;
        }
      }
    }
    if (c.excludes?.length) {
      for (const exKey of c.excludes) {
        if (coerceBool(mergedRuntimeFlagValues[exKey])) {
          return `Cannot be enabled with ${exKey}=true.`;
        }
      }
    }
    return null;
  };

  const runtimeSelectedPresetId = api?.runtimeState?.selectedPresetId ?? null;
  const selectedPresetValue = runtimeSelectedPresetId ? runtimeSelectedPresetId : "";
  const selectedInProduct = !!api?.presets?.some((p) => p.id === runtimeSelectedPresetId);
  const selectedExperimental = (Array.isArray(api?.experimentalPresets) ? api?.experimentalPresets : []).find(
    (p) => p.productPresetId === runtimeSelectedPresetId,
  );

  const presetKindBadge =
    api?.runtimeState?.preset?.kind === "DEFAULT"
      ? "Recommended"
      : api?.runtimeState?.preset?.kind === "PRODUCT"
        ? "Product"
        : api?.runtimeState?.preset?.kind === "EXPERIMENTAL"
          ? "Experimental"
          : api?.runtimeState?.preset?.kind === "MISSING"
            ? "Missing"
            : null;

  const presetSupportBadge =
    api?.runtimeState?.preset && !api.runtimeState.preset.chatSelectable
      ? formatPresetSupportMessage(
          api.runtimeState.preset.supportStatus,
          api.runtimeState.preset.reasonIfUnsupported,
          presetCopyT,
          "chatPresetNotSelectable",
        )
      : null;
  const selectedPresetDisabledReasonRaw =
    api?.runtimeState?.disabledPresetReason ??
    api?.runtimeState?.presetCompatibility?.disabledReason ??
    issueByField.get("preset")?.message ??
    issueByField.get("presetId")?.message ??
    null;
  const selectedPresetDisabledReason = selectedPresetDisabledReasonRaw
    ? mapUserFacingErrorMessage(selectedPresetDisabledReasonRaw, tLab, selectedPresetDisabledReasonRaw)
    : null;

  function presetIndexDisabledReason(p: {
    chatSelectable: boolean;
    supportStatus: string;
    reasonIfUnsupported: string | null;
    indexRequirements?: {
      requiredMaterializationStrategy: string | null;
      requiresMetadataSupport: boolean;
    } | null;
  }): string | null {
    if (!p.chatSelectable) {
      return formatPresetSupportMessage(p.supportStatus, p.reasonIfUnsupported, presetCopyT, "chatPresetNotSelectable");
    }
    const req = p.indexRequirements;
    if (!req) return null;
    const idx = api?.runtimeState?.indexCompatibility;
    const caps = idx?.activeSnapshotCapabilities;
    if (!idx?.hasActiveIndex || !caps) {
      return tChat("chatPresetRequiresCompatibleIndex");
    }
    const activeMat = caps.materializationStrategy;
    const requiredMat = req.requiredMaterializationStrategy;
    const matOk =
      !requiredMat ||
      activeMat === requiredMat ||
      (activeMat === "HYBRID" && requiredMat === "CHUNK_LEVEL");
    if (!matOk) {
      return tChat("chatPresetRequiresCompatibleIndex");
    }
    if (req.requiresMetadataSupport && caps.supportsMetadata !== true) {
      return tChat("chatPresetRequiresMetadata");
    }
    return null;
  }

  useEffect(() => {
    const known = new Set(
      [
        "useRetrieval",
        "naiveFullCorpusInPromptEnabled",
        "expansionEnabled",
        "toolsEnabled",
        "functionCallingEnabled",
        "nerEnabled",
        "useAdvisor",
        "reasoningEnabled",
        "rankerEnabled",
        "postRetrievalEnabled",
        "clarificationEnabled",
        "memoryEnabled",
        "adaptiveRoutingEnabled",
        "judgeEnabled",
      ].map(String),
    );
    const unknown = runtimeToggles.filter((c) => !known.has(c.key));
    if (unknown.length > 0 && process.env.NODE_ENV !== "production") {
      console.warn("[chat] Unknown runtime capability keys:", unknown.map((x) => x.key));
    }
  }, [runtimeToggles]);

  const documentScopeHint =
    api?.limitDocs && api?.limitDocsToggleNotice ? api.limitDocsToggleNotice : null;

  const instructionsPreview = useMemo(() => {
    const raw = mergedRuntimeFlagValues.llmSystemPrompt;
    if (typeof raw !== "string") return null;
    const trimmed = raw.trim();
    if (!trimmed) return null;
    return trimmed.length > 280 ? `${trimmed.slice(0, 277).trimEnd()}…` : trimmed;
  }, [mergedRuntimeFlagValues]);

  function formatIndexBoundCapabilityValue(key: string): string {
    const profile = indexProfileQuery.data;
    const snapCaps = api?.runtimeState?.indexCompatibility?.activeSnapshotCapabilities;
    const ec = effectiveConfig as EffectiveConfigMap | null;
    switch (key) {
      case "materializationStrategy": {
        if (profile?.materializationStrategy) return profile.materializationStrategy;
        const v = ec?.materializationStrategy;
        return v != null && v !== "" ? String(v) : notLoadedLabel;
      }
      case "metadataEnabled": {
        if (profile) return String(Boolean(profile.metadataEnabled));
        const v = ec?.metadataEnabled;
        return typeof v === "boolean" ? String(v) : notLoadedLabel;
      }
      case "embeddingModel": {
        const fromProfile = profile?.embeddingModelId;
        if (fromProfile) return fromProfile;
        const fromSnap = snapCaps?.embeddingModelId;
        if (fromSnap) return fromSnap;
        const v = ec?.embeddingModel;
        return v != null && String(v).trim() !== "" ? String(v) : notLoadedLabel;
      }
      case "chunkMaxChars": {
        if (profile != null) return String(profile.chunkMaxChars);
        if (snapCaps?.chunkMaxChars != null) return String(snapCaps.chunkMaxChars);
        return notLoadedLabel;
      }
      case "chunkOverlap": {
        if (profile?.chunkOverlap != null) return String(profile.chunkOverlap);
        if (snapCaps?.chunkOverlap != null) return String(snapCaps.chunkOverlap);
        return notLoadedLabel;
      }
      default:
        return notLoadedLabel;
    }
  }

  // Ensure the draft starts from persisted overrides, not from the preset.
  // (We still display effective config separately via validate.)
  const productIds = useMemo(() => new Set((api?.presets ?? []).map((p) => p.id)), [api?.presets]);
  const experimentalUnique = useMemo(
    () =>
      sortPresetsByRank(
        (api?.experimentalPresets ?? []).filter((p) => !productIds.has(p.productPresetId)),
      ),
    [api?.experimentalPresets, productIds],
  );

  const getBooleanValue = (key: string): boolean => {
    if (api?.runtimeState?.effectiveConfig) {
      return coerceBool((api.runtimeState.effectiveConfig as Record<string, unknown>)[key]);
    }
    return coerceBool(effectiveConfig ? (effectiveConfig as Record<string, unknown>)[key] : undefined);
  };

  const setOverrideBoolean = (key: string, next: boolean) => {
    const current = (api?.runtimeState?.runtimeOverride ?? api?.runtimeOverride ?? {}) as Record<string, unknown>;
    const updated = { ...current, [key]: next };
    api?.saveRuntimeOverride(updated);
  };

  const getNumberValue = (key: string, fallback: number): number => {
    const value = mergedRuntimeFlagValues[key];
    return typeof value === "number" && Number.isFinite(value) ? value : fallback;
  };

  const setOverrideNumber = (key: string, next: number) => {
    if (!Number.isFinite(next)) return;
    const current = (api?.runtimeState?.runtimeOverride ?? api?.runtimeOverride ?? {}) as Record<string, unknown>;
    api?.saveRuntimeOverride({ ...current, [key]: next });
  };

  const exportEffectiveConfig = () => {
    if (!effectiveConfig || typeof effectiveConfig !== "object") return;
    const text = JSON.stringify(effectiveConfig, null, 2);
    const blob = new Blob([text], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "chat-effective-config.json";
    a.click();
    URL.revokeObjectURL(url);
  };

  const effectiveEntriesSorted = useMemo(() => {
    if (!effectiveConfig || typeof effectiveConfig !== "object") return [];
    return Object.entries(effectiveConfig as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b));
  }, [effectiveConfig]);

  const baseEffectiveEntriesSorted = useMemo(() => {
    const base = api?.runtimeState?.baseEffectiveConfig;
    if (!base || typeof base !== "object") return [];
    return Object.entries(base as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b));
  }, [api?.runtimeState?.baseEffectiveConfig]);

  const retrievalEnabled = getBooleanValue("useRetrieval");
  const displayModelLabel = activeLlmModel.trim() || tChat("modelDefault");
  const displayEmbeddingModel = formatIndexBoundCapabilityValue("embeddingModel");
  const llmProviderLabel = formatLlmProviderLabel(api?.selectableLlmModelsEffectiveProvider);
  const displayPresetLabel =
    (selectedExperimental ? resolvePresetDisplayName(selectedExperimental, presetCopyT) : null) ||
    (api?.runtimeState?.preset?.label?.trim()
      ? toProductPresetDisplayName(api.runtimeState.preset.label.trim())
      : null) ||
    (() => {
      const hit = api?.presets?.find((p) => p.id === runtimeSelectedPresetId);
      return hit ? toProductPresetDisplayName(hit.name) : null;
    })() ||
    tChat("presetRecommendedDefault");
  const documentsReadyLabel =
    documentCounts.ready > 0
      ? tChat("configCompactDocumentsReady", { ready: documentCounts.ready })
      : tChat("configCompactDocumentsNone");
  const indexStatusLabel = useMemo(() => {
    if (api?.runtimeState?.requiresReindex) return tChat("configCompactIndexNeedsRebuild");
    const status = api?.runtimeState?.indexCompatibility?.compatibilityStatus;
    if (status === "INCOMPATIBLE") return tChat("configCompactIndexNeedsRebuild");
    if (activeSnapQuery.data?.id) return tChat("configCompactIndexReady");
    return tChat("configCompactIndexNotAvailable");
  }, [
    activeSnapQuery.data?.id,
    api?.runtimeState?.indexCompatibility?.compatibilityStatus,
    api?.runtimeState?.requiresReindex,
    tChat,
  ]);

  return (
    <div className="flex min-w-0 max-w-full flex-col gap-6 break-words [overflow-wrap:anywhere]" data-testid="chat-assistant-configuration-surface">
      {blockingIssues.length > 0 ? (
        <div data-testid="chat-error">
          <div
            className="rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm"
            data-testid="chat-runtime-blocking-banner"
            role="alert"
          >
            <p className="font-medium text-destructive">{tChat("chatConfigInvalidTitle")}</p>
            <ul className="mt-1 space-y-1 text-xs text-muted-foreground">
              {blockingIssues.map((issue) => (
                <li key={`${issue.code}-${issue.field ?? "global"}`}>
                  <span className="font-mono text-destructive" data-testid={`chat-error-code-${actionableIssueCode(issue) ?? "UNKNOWN"}`}>
                    {actionableIssueCode(issue) ?? "UNKNOWN"}
                  </span>
                  <span>
                    {" "}
                    —{" "}
                    {chatFailureHintForCode(issue.code, tChat) ??
                      formatRuntimeValidationIssueMessage(issue, tChat)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}

      {warningIssues.length > 0 ? (
        <div
          className="rounded-lg border bg-muted/30 px-3 py-2 text-xs text-muted-foreground"
          role="status"
          data-testid="chat-config-validation-warning"
        >
          {formatRuntimeValidationIssueMessage(warningIssues[0]!, tChat)}
        </div>
      ) : null}

      <div
        className="space-y-3 rounded-lg border bg-background/60 p-3"
        data-testid="chat-config-compact-summary"
      >
        <p className="text-sm font-medium">{tChat("configCompactTitle")}</p>
        <div className="space-y-2">
          <CompactSummaryRow
            label={tChat("configCompactModel")}
            value={
              <span className="break-words [overflow-wrap:anywhere]" data-testid="chat-config-summary-model">
                {displayModelLabel}
              </span>
            }
          />
          {retrievalEnabled && displayEmbeddingModel !== notLoadedLabel ? (
            <CompactSummaryRow
              label={tChat("chatEmbeddingModelLabel")}
              value={
                <span className="break-words [overflow-wrap:anywhere]" data-testid="chat-config-summary-embedding">
                  {displayEmbeddingModel}
                </span>
              }
            />
          ) : null}
          <CompactSummaryRow
            label={tChat("configCompactPreset")}
            value={<span data-testid="chat-config-summary-preset">{displayPresetLabel}</span>}
          />
          <CompactSummaryRow
            label={tChat("configCompactDocSearch")}
            value={
              <span data-testid="chat-config-summary-doc-search">
                {retrievalEnabled ? tChat("configCompactDocSearchOn") : tChat("configCompactDocSearchOff")}
              </span>
            }
          />
          <CompactSummaryRow
            label={tChat("configCompactDocuments")}
            value={<span data-testid="chat-config-summary-documents">{documentsReadyLabel}</span>}
          />
          <CompactSummaryRow
            label={tChat("configCompactIndex")}
            value={<span data-testid="chat-config-summary-index">{indexStatusLabel}</span>}
          />
          <CompactSummaryRow
            label={tChat("configSectionConversationMemory")}
            value={
              <span data-testid="chat-config-summary-memory">
                {getBooleanValue(MEMORY_FEATURE_KEY) ? tChat("configCompactDocSearchOn") : tChat("configCompactDocSearchOff")}
              </span>
            }
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            data-testid="chat-config-edit-button"
            className={cn(buttonVariants({ variant: "default", size: "sm" }))}
            onClick={() => setEditOpen((o) => !o)}
          >
            {editOpen ? tChat("configEditClose") : tChat("configEditButton")}
          </button>
        </div>
      </div>

      {editOpen ? (
        <>
      <Section title={tChat("configSectionDocumentScope")}>
        <Box>
          <div className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <label className="flex cursor-pointer items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  data-testid="chat-limit-documents-checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(api?.limitDocs)}
                  disabled={needsProject || needsConversation || Boolean(api?.limitDocsDisabled) || Boolean(api?.patchConvPending)}
                  onChange={(e) => api?.onLimitDocsChange(e.target.checked)}
                />
                <span>{tChat("limitDocumentsLabel")}</span>
              </label>
              {api?.limitDocsDisabled ? (
                <MenuHint>{tChat("limitDocumentsNoReadyHint")}</MenuHint>
              ) : null}
            </div>

            {documentScopeHint ? <MenuHint>{documentScopeHint}</MenuHint> : null}

            <div className="grid grid-cols-2 gap-2 rounded-md border bg-background/50 px-3 py-2 text-xs sm:grid-cols-4">
              <div>
                <span className="text-muted-foreground block">{tChat("configDocumentCountTotal")}</span>
                <span className="font-mono">{documentCounts.total}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">{tChat("configDocumentCountReady")}</span>
                <span className="font-mono">{documentCounts.ready}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">{tChat("configDocumentCountIngesting")}</span>
                <span className="font-mono">{documentCounts.ingesting}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">{tChat("configDocumentCountError")}</span>
                <span className="font-mono">{documentCounts.error}</span>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                data-testid="chat-open-documents-sheet"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsProject || needsConversation}
                onClick={() => api?.openDocumentsSheet()}
              >
                {tChat("manageDocuments")}
              </button>

              <input
                ref={uploadInputRef}
                type="file"
                className="sr-only"
                multiple
                aria-label={tChat("documentsSheetUploadInputAria")}
                onChange={(e) => api?.onAddDocuments(e.target.files)}
              />
              <button
                type="button"
                className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
                disabled={needsProject || needsConversation || Boolean(api?.uploadPending)}
                onClick={() => uploadInputRef.current?.click()}
              >
                {tChat("addDocuments")}
              </button>
            </div>
          </div>
        </Box>
      </Section>

      <p className="text-muted-foreground text-xs" data-testid="chat-config-scope-legend">
        {tChat("configScopeLegend")}
      </p>

      <Section title={tChat("configSectionAssistantInstructions")}>
        <Box>
          <div className="space-y-2" data-testid="chat-assistant-instructions-section">
            <MenuHint>{tChat("chatInstructionsEditInSettingsHint")}</MenuHint>
            {instructionsPreview ? (
              <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                <p className="text-muted-foreground mb-1 font-medium">{tChat("chatInstructionsPreviewLabel")}</p>
                <p className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">{instructionsPreview}</p>
              </div>
            ) : (
              <output className="text-muted-foreground text-xs">{tChat("chatInstructionsEmpty")}</output>
            )}
          </div>
        </Box>
      </Section>

      <Section title={tChat("configSectionModelConfiguration")}>
        <Box>
          <div className="space-y-3">
            <div className="flex flex-col gap-1">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor={modelSelectId} className="text-xs">
                  {tChat("modelLabel")}
                </Label>
                <ConfigScopeBadge
                  scope={scopeForRuntimeKey("llmModel")}
                  label={scopeLabel(scopeForRuntimeKey("llmModel"))}
                />
              </div>
              <select
                id={modelSelectId}
                data-testid="chat-llm-model-select"
                data-effective-provider={api?.selectableLlmModelsEffectiveProvider ?? undefined}
                className={cn(
                  "border-input bg-background h-9 min-w-0 max-w-full w-full rounded-md border px-2 text-sm",
                  (api?.modelsError || llmSelectionInvalid) && "border-destructive",
                )}
                value={api?.llmModelChoice ?? ""}
                onChange={(e) => api?.setLlmModelChoice(e.target.value)}
                disabled={
                  needsProject ||
                  needsConversation ||
                  !!api?.modelsError ||
                  api?.selectableLlmModelsLoading ||
                  patchPending
                }
                aria-label={tChat("modelLabel")}
              >
                <option value="">{tChat("modelDefault")}</option>
                {api?.selectableLlmModelsLoading ? (
                  <option value="" disabled>
                    {tChat("modelsLoading")}
                  </option>
                ) : null}
                {llmSelectionInvalid ? (
                  <option value={selectedLlmModel} data-testid="chat-llm-model-invalid-option">
                    {selectedLlmModel}
                  </option>
                ) : null}
                {[...(api?.selectableLlmModels ?? [])]
                  .sort((a, b) => a.modelName.localeCompare(b.modelName))
                  .map((m) => (
                    <option
                      key={m.modelName}
                      value={m.modelName}
                      disabled={!m.selectable}
                      title={m.disabledReason ?? undefined}
                    >
                      {m.displayName || m.modelName}
                      {!m.selectable ? ` (${tChat("modelUnavailable")})` : ""}
                    </option>
                  ))}
              </select>
              {llmProviderLabel ? (
                <p
                  className="text-muted-foreground text-xs break-words [overflow-wrap:anywhere]"
                  data-testid="chat-llm-model-provider"
                >
                  {tChat("chatLlmCatalogProvider", { provider: llmProviderLabel })}
                </p>
              ) : null}
              {api?.selectableLlmModelsLoading ? (
                <output className="text-muted-foreground text-xs" data-testid="chat-llm-models-loading">
                  {tChat("modelsLoading")}
                </output>
              ) : null}
              {!api?.selectableLlmModelsLoading &&
              !api?.modelsError &&
              (api?.selectableLlmModels?.length ?? 0) === 0 ? (
                <output className="text-muted-foreground text-xs" data-testid="chat-llm-model-catalog-empty">
                  {tChat("modelsCatalogEmpty")}
                </output>
              ) : null}
              {llmSelectionInvalid ? (
                <output className="text-destructive text-xs" data-testid="chat-llm-model-selection-invalid">
                  {tChat("modelSelectionInvalid", { modelId: selectedLlmModel })}
                </output>
              ) : null}
              {api?.modelsError ? (
                <output className="text-destructive text-xs" data-testid="chat-error-code-MODEL_UNAVAILABLE">
                  {api.modelsErrorMessage || tChat("chatJobFailure_MODEL_UNAVAILABLE")}
                </output>
              ) : null}
              {!api?.modelsError &&
              !api?.selectableLlmModelsLoading &&
              (api?.selectableLlmModels ?? []).some((m) => !m.selectable && m.disabledReason) ? (
                <ul className="text-muted-foreground space-y-1 text-xs" data-testid="chat-llm-model-unavailable-hints">
                  {(api?.selectableLlmModels ?? [])
                    .filter((m) => !m.selectable && m.disabledReason)
                    .map((m) => (
                      <li key={m.modelName}>
                        {m.modelName}: {m.disabledReason}
                      </li>
                    ))}
                </ul>
              ) : null}
            </div>

            <div className="flex flex-col gap-1">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor={classifierSelectId} className="text-xs">
                  {tChat("classifierLabel")}
                </Label>
                <ConfigScopeBadge
                  scope={scopeForRuntimeKey("classifierModelId")}
                  label={scopeLabel(scopeForRuntimeKey("classifierModelId"))}
                />
              </div>
              <select
                id={classifierSelectId}
                data-testid="chat-classifier-select"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  classifierModelsQuery.isError && "border-destructive",
                )}
                value={api?.classifierModelChoice ?? ""}
                onChange={(e) => api?.setClassifierModelChoice(e.target.value)}
                disabled={needsProject || needsConversation || patchPending}
                aria-label={tChat("classifierLabel")}
              >
                <option value="">{tChat("classifierDefault")}</option>
                {(classifierModelsQuery.data ?? []).map((m) => (
                  <option key={m.id} value={m.inferenceTag}>
                    {m.name} ({m.inferenceTag})
                  </option>
                ))}
              </select>
              {classifierModelsQuery.isError ? (
                <output className="text-destructive text-xs" data-testid="chat-error-code-CLASSIFIER_UNAVAILABLE">
                  {tChat("classifierLoadError")} {tChat("chatJobFailure_CLASSIFIER_UNAVAILABLE")}
                </output>
              ) : null}
            </div>
          </div>
        </Box>
      </Section>

      <Section title={tChat("configSectionEmbeddingModel")}>
        <Box>
          {embeddingIndexBoundCap ? (
            <div className="flex min-w-0 flex-col gap-1" data-testid="chat-embedding-model-readonly">
              <div className="flex items-center justify-between gap-2">
                <Label className="text-xs">{tChat("chatEmbeddingModelLabel")}</Label>
                <ConfigScopeBadge scope="project" label={scopeLabel("project")} />
              </div>
              <div
                className="border-input bg-muted/30 min-w-0 break-words rounded-md border px-2 py-2 text-sm [overflow-wrap:anywhere]"
                data-testid="chat-embedding-model-value"
              >
                {displayEmbeddingModel}
              </div>
              <p className="text-muted-foreground text-xs break-words [overflow-wrap:anywhere]">
                {embeddingIndexBoundCap.reasonIfDisabled ?? tChat("chatEmbeddingModelReadOnlyHint")}
              </p>
            </div>
          ) : (
            <output className="text-muted-foreground text-xs">{notLoadedLabel}</output>
          )}
        </Box>
      </Section>

      <Section title={tChat("configSectionConfigurationProfile")}>
        <Box>
          <div className="space-y-3">
            <div className="flex flex-col gap-1">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor={presetSelectId} className="text-xs">
                  {tChat("presetLabel")}
                </Label>
                <div className="flex items-center gap-2">
                  {presetKindBadge ? (
                    <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                      {presetKindBadge}
                    </span>
                  ) : null}
                  {selectedExperimental ? (
                    <span
                      className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium"
                      title={formatChatPresetTechnicalTitle(selectedExperimental, presetCopyT)}
                      data-testid="chat-preset-experimental-badge"
                    >
                      {tChat("presetExperimentalBadge")}
                    </span>
                  ) : null}
                  {hasCustomOverride ? (
                    <span
                      className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium"
                      data-testid="chat-custom-state"
                    >
                      Custom
                    </span>
                  ) : null}
                  {presetSupportBadge ? (
                    <span
                      className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium"
                      data-testid="chat-preset-support-badge"
                    >
                      {presetSupportBadge}
                    </span>
                  ) : null}
                </div>
              </div>

              <select
                id={presetSelectId}
                data-testid="chat-preset-select"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  api?.presetsError && "border-destructive",
                )}
                value={selectedPresetValue}
                onChange={(e) => api?.onPresetChange(e.target.value)}
                disabled={needsProject || needsConversation || !!api?.presetSelectDisabled}
                aria-label={tChat("presetLabel")}
              >
                <option value="">{tChat("presetRecommendedDefault")}</option>
                {runtimeSelectedPresetId &&
                !selectedInProduct &&
                !selectedExperimental &&
                api?.runtimeState?.preset?.label ? (
                  <option value={runtimeSelectedPresetId}>{api.runtimeState.preset.label}</option>
                ) : null}
                <optgroup label={tChat("presetGroupProduct")}>
                  {api?.presets?.map((p) => (
                    <option key={p.id} value={p.id}>
                      {toProductPresetDisplayName(p.name)}
                    </option>
                  ))}
                </optgroup>
                <optgroup label={tChat("presetGroupExperimental")}>
                  {experimentalUnique.map((p) => {
                    const reason = presetIndexDisabledReason(p);
                    const optionLabel =
                      reason && p.chatSelectable
                        ? `${formatChatExperimentalPresetOptionLabel(p, presetCopyT)} (${reason})`
                        : formatChatExperimentalPresetOptionLabel(p, presetCopyT);
                    return (
                      <option
                        key={p.productPresetId}
                        value={p.productPresetId}
                        disabled={Boolean(reason)}
                        title={formatChatPresetTechnicalTitle(p, presetCopyT)}
                      >
                        {optionLabel}
                      </option>
                    );
                  })}
                </optgroup>
              </select>

              {selectedPresetDisabledReason ? (
                <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-xs">
                  <p className="font-medium text-destructive">{selectedPresetDisabledReason}</p>
                  {api?.runtimeState?.requiresReindex ? (
                    <p className="mt-1 text-muted-foreground">
                      Create or reindex project with compatible profile.
                    </p>
                  ) : null}
                </div>
              ) : null}
              {!api?.presetsLoading && !api?.presetsError && (api?.presets?.length ?? 0) === 0 ? (
                <output className="text-muted-foreground text-xs">{tChat("presetCatalogEmpty")}</output>
              ) : null}
              {api?.presetsError ? (
                <output className="text-destructive text-xs">{tChat("presetsLoadError")}</output>
              ) : null}
            </div>
          </div>
        </Box>
      </Section>

      <Section title={tChat("configSectionRetrievalSettings")}>
        <Box>
          <div className="space-y-4" data-testid="chat-retrieval-settings-section">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-sm">
                <span className="flex items-center justify-between gap-2">
                  {tChat("configRetrievalTopKLabel")}
                  <ConfigScopeBadge
                    scope={scopeForRuntimeKey("topK")}
                    label={scopeLabel(scopeForRuntimeKey("topK"))}
                  />
                </span>
                <input
                  data-testid="chat-runtime-toggle-topK"
                  type="number"
                  min={1}
                  max={100}
                  className="border-input bg-background h-9 rounded-md border px-2 text-sm"
                  value={getNumberValue("topK", 5)}
                  disabled={patchPending}
                  onChange={(e) => setOverrideNumber("topK", Number(e.target.value))}
                />
              </label>
              <label className="flex flex-col gap-1 text-sm">
                <span className="flex items-center justify-between gap-2">
                  {tChat("configRetrievalSimilarityLabel")}
                  <ConfigScopeBadge
                    scope={scopeForRuntimeKey("similarityThreshold")}
                    label={scopeLabel(scopeForRuntimeKey("similarityThreshold"))}
                  />
                </span>
                <input
                  data-testid="chat-runtime-toggle-similarityThreshold"
                  type="number"
                  min={0}
                  max={1}
                  step={0.01}
                  className="border-input bg-background h-9 rounded-md border px-2 text-sm"
                  value={getNumberValue("similarityThreshold", 0)}
                  disabled={patchPending}
                  onChange={(e) => setOverrideNumber("similarityThreshold", Number(e.target.value))}
                />
              </label>
            </div>
            <div className="grid grid-cols-1 gap-3">
              {retrievalRuntimeToggles.map((cap) => {
                const key = cap.key;
                const reason = disabledReason(key);
                const fieldIssue = issueByField.get(key);
                const rid = `disabled-${key}`;
                const labelKey = chatRuntimeLabelKey(key);
                const label = tChat(labelKey) !== labelKey ? tChat(labelKey) : cap.label ?? key;
                return (
                  <div key={key} className="flex flex-col gap-1">
                    <div className="flex items-start justify-between gap-3">
                      <label className="flex cursor-pointer items-center gap-3 text-sm">
                        <input
                          type="checkbox"
                          data-testid={`chat-runtime-toggle-${key}`}
                          className="border-input size-4 rounded"
                          checked={getBooleanValue(key)}
                          disabled={!!reason || patchPending}
                          onChange={(e) => setOverrideBoolean(key, e.target.checked)}
                          aria-describedby={reason ? rid : undefined}
                        />
                        <span>{label}</span>
                      </label>
                      <ConfigScopeBadge scope={scopeForRuntimeKey(key)} label={scopeLabel(scopeForRuntimeKey(key))} />
                    </div>
                    {fieldIssue ? <MenuHint>{fieldIssue.message}</MenuHint> : null}
                    {reason ? <DisabledReason id={rid} reason={reason} label={tChat("configDisabledLabel")} /> : null}
                  </div>
                );
              })}
            </div>
          </div>
        </Box>
      </Section>

      {memoryRuntimeCap ? (
        <Section title={tChat("configSectionConversationMemory")}>
          <Box data-testid="chat-conversation-memory-section">
            <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
              <span className="flex items-center gap-3">
                <input
                  type="checkbox"
                  data-testid="chat-runtime-toggle-memoryEnabled"
                  className="border-input size-4 rounded"
                  checked={getBooleanValue(MEMORY_FEATURE_KEY)}
                  disabled={!!disabledReason(MEMORY_FEATURE_KEY) || patchPending}
                  onChange={(e) => setOverrideBoolean(MEMORY_FEATURE_KEY, e.target.checked)}
                />
                <span>{tChat("runtimeFeatureMemoryEnabled")}</span>
                {memoryRuntimeCap.supportMode === "MULTI_TURN_REQUIRED" ? (
                  <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">Multi-turn</span>
                ) : null}
              </span>
              <ConfigScopeBadge
                scope={scopeForRuntimeKey(MEMORY_FEATURE_KEY)}
                label={scopeLabel(scopeForRuntimeKey(MEMORY_FEATURE_KEY))}
              />
            </label>
            {memoryRuntimeCap.supportMode === "MULTI_TURN_REQUIRED" ? (
              <MenuHint>{tChat("runtimeMultiTurnHint")}</MenuHint>
            ) : null}
          </Box>
        </Section>
      ) : null}

      {clarificationRuntimeCap ? (
        <Section title={tChat("configSectionClarification")}>
          <Box data-testid="chat-clarification-section">
            <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
              <span className="flex items-center gap-3">
                <input
                  type="checkbox"
                  data-testid="chat-runtime-toggle-clarificationEnabled"
                  className="border-input size-4 rounded"
                  checked={getBooleanValue(CLARIFICATION_FEATURE_KEY)}
                  disabled={!!disabledReason(CLARIFICATION_FEATURE_KEY) || patchPending}
                  onChange={(e) => setOverrideBoolean(CLARIFICATION_FEATURE_KEY, e.target.checked)}
                />
                <span>{tChat("runtimeFeatureClarificationEnabled")}</span>
                {clarificationRuntimeCap.supportMode === "MULTI_TURN_REQUIRED" ? (
                  <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">Multi-turn</span>
                ) : null}
              </span>
              <ConfigScopeBadge
                scope={scopeForRuntimeKey(CLARIFICATION_FEATURE_KEY)}
                label={scopeLabel(scopeForRuntimeKey(CLARIFICATION_FEATURE_KEY))}
              />
            </label>
            {clarificationRuntimeCap.supportMode === "MULTI_TURN_REQUIRED" ? (
              <MenuHint>{tChat("runtimeMultiTurnHint")}</MenuHint>
            ) : null}
          </Box>
        </Section>
      ) : null}

      {answerQualityRuntimeCap ? (
        <Section title={tChat("configSectionAnswerQualityChecks")}>
          <Box data-testid="chat-answer-quality-section">
            <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
              <span className="flex items-center gap-3">
                <input
                  type="checkbox"
                  data-testid="chat-runtime-toggle-judgeEnabled"
                  className="border-input size-4 rounded"
                  checked={getBooleanValue(ANSWER_QUALITY_FEATURE_KEY)}
                  disabled={!!disabledReason(ANSWER_QUALITY_FEATURE_KEY) || patchPending}
                  onChange={(e) => setOverrideBoolean(ANSWER_QUALITY_FEATURE_KEY, e.target.checked)}
                />
                <span>{tChat("runtimeFeatureAnswerQualityChecks")}</span>
              </span>
              <ConfigScopeBadge
                scope={scopeForRuntimeKey(ANSWER_QUALITY_FEATURE_KEY)}
                label={scopeLabel(scopeForRuntimeKey(ANSWER_QUALITY_FEATURE_KEY))}
              />
            </label>
          </Box>
        </Section>
      ) : null}
        </>
      ) : null}

      <details
        className="rounded-lg border bg-muted/20 p-3 text-xs"
        data-testid="chat-config-current-settings"
      >
        <summary className="cursor-pointer text-sm font-medium text-foreground">
          {tChat("runtimeCurrentSettingsTitle")}
        </summary>
        <div className="mt-3 space-y-3">
          <p className="text-muted-foreground text-xs">{tChat("runtimeEffectiveCollapsedHint")}</p>

          <details
            className="rounded-md border bg-background/40 p-3"
            data-testid="chat-config-advanced-technical"
          >
            <summary className="cursor-pointer text-xs font-medium">{tChat("configAdvancedTechnicalSummary")}</summary>
            <div className="mt-3 space-y-2">
              <MenuHint>{tChat("chatConfigIndexProfileReindexHint")}</MenuHint>

              {activeSnapQuery.data ? (
                <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigActiveSnapshotLabel")}</span>
                    <span className="font-mono">{activeSnapQuery.data.id}</span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigSnapshotStatusLabel")}</span>
                    <span className="font-mono">{activeSnapQuery.data.status}</span>
                  </div>
                  {activeSnapQuery.data.indexProfileHash ? (
                    <div className="mt-1 flex items-center justify-between gap-3">
                      <span className="text-muted-foreground">{tChat("chatConfigProfileHashLabel")}</span>
                      <span className="font-mono">{activeSnapQuery.data.indexProfileHash}</span>
                    </div>
                  ) : null}
                </div>
              ) : (
                <div data-testid="chat-snapshot-warning">
                  <MenuHint>{tChat("chatConfigNoActiveIndexHint")}</MenuHint>
                </div>
              )}

              {api?.runtimeState?.indexCompatibility?.presetIndexRequirements ? (
                <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigPresetIndexRequirements")}</span>
                    <span className="font-mono">
                      {api.runtimeState.indexCompatibility.presetIndexRequirements.requiredMaterializationStrategy ?? "NONE"}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigRequiresMetadataSupport")}</span>
                    <span className="font-mono">
                      {String(Boolean(api.runtimeState.indexCompatibility.presetIndexRequirements.requiresMetadataSupport))}
                    </span>
                  </div>
                  {api.runtimeState.indexCompatibility.compatibilityStatus ? (
                    <div className="mt-1 flex items-center justify-between gap-3">
                      <span className="text-muted-foreground">{tChat("chatConfigCompatibilityLabel")}</span>
                      <span
                        className={cn(
                          "font-mono",
                          api.runtimeState.requiresReindex ? "text-destructive" : "text-foreground",
                        )}
                      >
                        {api.runtimeState.indexCompatibility.compatibilityStatus}
                      </span>
                    </div>
                  ) : null}
                </div>
              ) : null}

              {activeSnapshotCapabilities ? (
                <div className="rounded-md border bg-background/50 px-3 py-2 text-xs" data-testid="chat-index-info">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigMaterializationStrategy")}</span>
                    <span className="font-mono">{activeSnapshotCapabilities.materializationStrategy ?? notLoadedLabel}</span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatConfigMetadataSupport")}</span>
                    <span className="font-mono">{String(Boolean(activeSnapshotCapabilities.supportsMetadata))}</span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">{tChat("chatEmbeddingModelLabel")}</span>
                    <span className="font-mono">{activeSnapshotCapabilities.embeddingModelId ?? notLoadedLabel}</span>
                  </div>
                  {activeSnapshotCapabilities.chunkMaxChars != null ? (
                    <div className="mt-1 flex items-center justify-between gap-3">
                      <span className="text-muted-foreground">{tChat("chatConfigChunkMaxCharsLabel")}</span>
                      <span className="font-mono">{activeSnapshotCapabilities.chunkMaxChars}</span>
                    </div>
                  ) : null}
                </div>
              ) : null}

              {api?.runtimeState?.requiresReindex ? (
                <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-xs" data-testid="chat-snapshot-warning">
                  <p className="font-medium text-destructive">{tChat("chatConfigReindexRequiredTitle")}</p>
                  <p className="text-muted-foreground mt-1">{tChat("chatConfigReindexRequiredBody")}</p>
                </div>
              ) : null}

              <div className="grid grid-cols-1 gap-2 text-sm">
                {indexBoundCapsWithoutEmbedding.map((cap) => {
                  const reason = cap.reasonIfDisabled ?? tChat("chatConfigIndexCapabilityLockedReason");
                  return (
                    <div key={cap.key} className="flex flex-col gap-0.5 rounded-md border border-dashed bg-muted/20 px-3 py-2">
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-muted-foreground">{cap.label ?? cap.key}</span>
                        <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] font-medium uppercase text-muted-foreground">
                          {tChat("chatConfigLockedCapabilityBadge")}
                        </span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-[11px] leading-snug text-muted-foreground">{reason}</span>
                        <span className="shrink-0 break-all text-xs">{formatIndexBoundCapabilityValue(cap.key)}</span>
                      </div>
                    </div>
                  );
                })}
              </div>

              {effectiveConfig ? (
                <div
                  className="grid max-h-64 grid-cols-1 gap-1 overflow-y-auto text-xs"
                  data-testid="chat-config-effective-keys"
                >
                  <output className="text-muted-foreground mb-1 block text-[11px]">
                    {tChat("runtimeEffectiveKeyCount", { count: effectiveEntriesSorted.length })}
                  </output>
                  {effectiveEntriesSorted.map(([k, v]) => (
                    <div key={k} className="flex items-baseline justify-between gap-3">
                      <span className="truncate font-mono">
                        {k}
                        {manualOverrideKeySet.has(k) ? (
                          <span className="ml-2 text-muted-foreground">{tChat("runtimeCustomMarker")}</span>
                        ) : null}
                      </span>
                      <span className="font-mono text-muted-foreground">
                        {typeof v === "string" || typeof v === "number" || typeof v === "boolean" ? String(v) : "[object]"}
                      </span>
                    </div>
                  ))}
                </div>
              ) : null}

              <div className="rounded-lg border bg-background/40 p-3" data-testid="chat-config-effective-block">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium">{tChat("runtimeEffectiveTitle")}</p>
                  <button
                    type="button"
                    data-testid="chat-config-export-effective"
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                    disabled={!effectiveConfig || needsProject || needsConversation}
                    onClick={exportEffectiveConfig}
                  >
                    {tChat("runtimeEffectiveExport")}
                  </button>
                </div>
                {effectiveError ? (
                  <p className="text-destructive mt-2 text-xs" role="alert">
                    {effectiveError}
                  </p>
                ) : null}
                {api?.runtimeState ? (
                  <div className="mt-2 space-y-2 text-[11px]">
                    <p className="text-muted-foreground font-medium uppercase tracking-wide">{tChat("runtimeLayersTitle")}</p>
                    <p className="text-muted-foreground">
                      {tChat("runtimeCustomLabel")}{" "}
                      <span className="font-mono">
                        {api.runtimeState.manualOverrideKeys?.length
                          ? api.runtimeState.manualOverrideKeys.join(", ")
                          : tChat("runtimeCustomEmpty")}
                      </span>
                    </p>
                  </div>
                ) : null}
                {api?.runtimeState?.baseEffectiveConfig && baseEffectiveEntriesSorted.length > 0 ? (
                  <details className="mt-2 rounded-md border bg-background/30 p-2 text-xs">
                    <summary className="cursor-pointer font-medium">{tChat("runtimeBaseExpand")}</summary>
                    <div className="mt-2 grid max-h-48 grid-cols-1 gap-1 overflow-y-auto text-[11px]">
                      {baseEffectiveEntriesSorted.map(([k, v]) => (
                        <div key={k} className="flex items-baseline justify-between gap-3">
                          <span className="truncate font-mono">{k}</span>
                          <span className="font-mono text-muted-foreground">
                            {typeof v === "string" || typeof v === "number" || typeof v === "boolean" ? String(v) : "[object]"}
                          </span>
                        </div>
                      ))}
                    </div>
                  </details>
                ) : null}
                {effectiveConfig && typeof effectiveConfig === "object" ? (
                  <pre
                    className="bg-muted/40 mt-2 max-h-40 overflow-auto rounded-md border p-2 text-[10px]"
                    data-testid="chat-config-effective-json"
                  >
                    {JSON.stringify(effectiveConfig, null, 2)}
                  </pre>
                ) : null}
              </div>
            </div>
          </details>
        </div>
      </details>

      <Section title={tChat("configAdvancedSection")}>
        <Box>
          <div className="space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <button
                  type="button"
                  data-testid="chat-config-runtime-collapsible"
                  className="hover:bg-muted inline-flex w-full items-center justify-between gap-3 rounded-md px-2 py-1 text-left"
                  aria-expanded={runtimeOpen}
                  onClick={() => setRuntimeOpen((p) => !p)}
                >
                  <span className="text-sm font-medium">{tChat("configAdvancedSection")}</span>
                  <span className="text-muted-foreground text-xs">{runtimeOpen ? tChat("configHide") : tChat("configShow")}</span>
                </button>
                {!runtimeOpen ? (
                  <p className="text-muted-foreground text-xs">{tChat("runtimeEffectiveCollapsedHint")}</p>
                ) : null}
              </div>
              <button
                type="button"
                data-testid="chat-config-runtime-refresh-effective"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsProject || needsConversation || effectiveLoading}
                onClick={() => api?.refreshRuntimeState()}
              >
                {effectiveLoading ? tChat("configLoadingShort") : tChat("configRefresh")}
              </button>
            </div>

            {runtimeOpen ? (
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-3">
                  {advancedRuntimeToggles.map((cap) => {
                    const key = cap.key;
                    const reason = disabledReason(key);
                    const fieldIssue = issueByField.get(key);
                    const rid = `disabled-${key}`;
                    const showMultiTurn = cap.supportMode === "MULTI_TURN_REQUIRED";
                    const labelKey = chatRuntimeLabelKey(key);
                    const label = tChat(labelKey) !== labelKey ? tChat(labelKey) : cap.label ?? key;
                    return (
                      <div key={key} className="flex flex-col gap-1">
                        <div className="flex items-start justify-between gap-3">
                          <label className="flex cursor-pointer items-center gap-3 text-sm">
                            <input
                              type="checkbox"
                              data-testid={`chat-runtime-toggle-${key}`}
                              className="border-input size-4 rounded"
                              checked={getBooleanValue(key)}
                              disabled={!!reason || patchPending}
                              onChange={(e) => setOverrideBoolean(key, e.target.checked)}
                              aria-describedby={reason ? rid : undefined}
                            />
                            <span>{label}</span>
                          </label>
                          <div className="flex items-center gap-2">
                            {showMultiTurn ? (
                              <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                                Multi-turn
                              </span>
                            ) : null}
                            {reason ? <DisabledReason id={rid} reason={reason} label={tChat("configDisabledLabel")} /> : null}
                          </div>
                        </div>
                        {fieldIssue ? (
                          <MenuHint>{fieldIssue.message}</MenuHint>
                        ) : null}
                        {showMultiTurn ? <MenuHint>{tChat("runtimeMultiTurnHint")}</MenuHint> : null}
                      </div>
                    );
                  })}
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                    disabled={needsConversation || !!api?.patchConvPending}
                    onClick={() => {
                      api?.clearRuntimeOverride();
                      setAdvancedError(null);
                      setAdvancedValidationText("Cleared.");
                    }}
                  >
                    Clear
                  </button>
                </div>

                {advancedError ? (
                  <p className="text-destructive text-xs" role="alert">
                    {advancedError}
                  </p>
                ) : null}
                {advancedValidationText ? (
                  <p className="text-muted-foreground text-xs">{advancedValidationText}</p>
                ) : null}
              </div>
            ) : null}

            <div className="border-border border-t pt-4">
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  data-testid="chat-move-project-button"
                  className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                  disabled={needsProject || needsConversation}
                  onClick={() => {
                    if (needsProject || needsConversation) return;
                    api?.openMoveDialog();
                  }}
                >
                  {t("chatMoveProject")}
                </button>
                <button
                  type="button"
                  data-testid="chat-delete-menu-item"
                  className={cn(buttonVariants({ variant: "destructive", size: "sm" }))}
                  disabled={needsProject || needsConversation}
                  onClick={() => {
                    if (needsProject || needsConversation) return;
                    api?.openDeleteForActiveConversation();
                  }}
                >
                  {t("chatDelete")}
                </button>
              </div>
            </div>
          </div>
        </Box>
      </Section>
    </div>
  );
}

