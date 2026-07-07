"use client";

import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Link } from "@/navigation";
import { useTranslations } from "next-intl";
import { formatProductPresetOptionLabel, formatProductPresetOptionTitle } from "@/features/chat/lib/preset-latency-tier";
import { formatChatExperimentalPresetOptionLabel, formatPresetSupportMessage } from "@/lib/product-copy";
import { createPresetCopyFn } from "@/lib/preset-copy-i18n";
import {
  formatChatPresetTechnicalTitle,
  resolvePresetDisplayName,
  sortPresetsByRank,
} from "@/features/presets/lib/preset-display";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { useClassifierModelsQuery } from "@/features/lab/hooks/use-classifier-registry";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import {
  compatibilityByExperimentalPresetId,
  compatibilityByProductPresetId,
  filterExperimentalPresetsForSelector,
  filterProductPresetsForSelector,
  presetCompatibilityDisabledReason,
  presetProductTierLabel,
  projectCompatiblePresetsEmptyState,
} from "@/features/chat/lib/chat-preset-compatibility";
import { formatPresetCompatibilityDisabledReason } from "@/features/chat/lib/preset-compatibility-reason";
import { useActiveProjectSnapshot } from "@/features/projects/hooks/use-active-project-snapshot";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import {
  CompactSummaryRow,
} from "@/features/chat/components/chat-config-compact-ui";
import {
  formatAdvancedTechnicalValidationIssue,
  formatRuntimeValidationIssueMessage,
  isAdvancedTechnicalValidationIssue,
} from "@/features/chat/lib/runtime-validation-copy";
import { formatDisabledRuntimeFeatureTip, clientSideDisableTip } from "@/features/chat/lib/runtime-feature-disable-copy";
import {
  isPresetBaseFeature,
  isPresetControlledOffFeature,
} from "@/features/chat/lib/preset-base-feature-locking";
import type { DisabledRuntimeFeatureDto } from "@/types/api";
import { cn } from "@/lib/utils";
import { productProviderLabel, productProviderLabelsFromSettings } from "@/lib/product-provider-labels";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import {
  ADVANCED_RUNTIME_ONLY_KEYS,
  ANSWER_QUALITY_FEATURE_KEY,
  chatRuntimeLabelKey,
  CLARIFICATION_FEATURE_KEY,
  MEMORY_FEATURE_KEY,
  RETRIEVAL_SETTING_KEYS,
} from "@/features/chat/lib/assistant-config-product-labels";
import { ConfigScopeBadge, resolveFieldScope, type AssistantConfigScope } from "@/features/chat/lib/assistant-config-scope";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";
import { useProjectStoredRagConfigQuery } from "@/features/settings/hooks/use-rag-config";
import { ChatEffectiveRuntimeSummary } from "@/features/chat/components/ChatEffectiveRuntimeSummary";
import { retrievalParameterSourceLabelKey } from "@/features/chat/lib/retrieval-parameter-source";
import {
  buildRetrievalModePatch,
  inferRetrievalOverrideMode,
  RETRIEVAL_OVERRIDE_MODE_KEY,
  sanitizeRuntimeOverridePatch,
  toRetrievalDefaults,
  type RetrievalOverrideMode,
} from "@/features/chat/lib/retrieval-override-mode";
import { useConversationConfigurationDraft } from "@/features/chat/hooks/use-conversation-configuration-draft";
import { useRetrievalFieldDrafts } from "@/features/chat/hooks/use-retrieval-field-drafts";
import { RetrievalNumericField } from "@/features/chat/components/RetrievalNumericField";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

const RETRIEVAL_TOP_K_CONSTRAINTS = { min: 1, max: 100 } as const;
const RETRIEVAL_SIMILARITY_CONSTRAINTS = { min: 0, max: 1 } as const;

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

function Box({
  children,
  className,
  "data-testid": dataTestId,
}: Readonly<{
  children: React.ReactNode;
  className?: string;
  "data-testid"?: string;
}>) {
  return (
    <div
      className={cn("min-w-0 max-w-full rounded-lg border bg-background/60 p-3", className)}
      data-testid={dataTestId}
    >
      {children}
    </div>
  );
}

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block min-w-0 max-w-full break-words text-xs font-normal leading-snug [overflow-wrap:anywhere]">
      {children}
    </output>
  );
}

function FeatureDisableTip({ tip, testId }: Readonly<{ tip: string; testId: string }>) {
  return (
    <span
      data-testid={testId}
      className="text-muted-foreground mt-0.5 block min-w-0 text-[11px] font-normal leading-snug"
    >
      {tip}
    </span>
  );
}

function LabelWithScopeBadge({
  label,
  scope,
  scopeLabelText,
}: Readonly<{
  label: ReactNode;
  scope: AssistantConfigScope;
  scopeLabelText: string;
}>) {
  return (
    <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
      <span className="min-w-0 break-words">{label}</span>
      <ConfigScopeBadge scope={scope} label={scopeLabelText} className="shrink-0" />
    </span>
  );
}

function RuntimeCheckboxFeatureRow({
  testId,
  checked,
  disabled,
  onChange,
  label,
  scope,
  scopeLabelText,
  multiTurn = false,
  multiTurnHint,
  disabledReasonNode,
  presetBadge,
  fieldIssue,
  describedById,
}: Readonly<{
  testId: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  scope: AssistantConfigScope;
  scopeLabelText: string;
  multiTurn?: boolean;
  multiTurnHint?: ReactNode;
  disabledReasonNode?: ReactNode;
  presetBadge?: ReactNode;
  fieldIssue?: ReactNode;
  describedById?: string;
}>) {
  return (
    <div className="flex min-w-0 items-start gap-2">
      <input
        id={testId}
        type="checkbox"
        data-testid={testId}
        className="border-input mt-1 size-4 shrink-0 rounded"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        aria-describedby={describedById}
      />
      <div className="min-w-0 flex-1">
        <label
          htmlFor={testId}
          className="flex min-w-0 cursor-pointer flex-wrap items-center gap-x-2 gap-y-1 text-sm"
        >
          <span className="min-w-0 break-words">{label}</span>
          {presetBadge}
          {multiTurn ? (
            <span className="shrink-0 rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">Multi-turn</span>
          ) : null}
          <ConfigScopeBadge scope={scope} label={scopeLabelText} className="shrink-0" />
        </label>
        {disabledReasonNode}
        {fieldIssue ? <MenuHint>{fieldIssue}</MenuHint> : null}
        {multiTurn && multiTurnHint ? <MenuHint>{multiTurnHint}</MenuHint> : null}
      </div>
    </div>
  );
}

type EffectiveConfigMap = Record<string, unknown>;

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
  const presetCopyT = createPresetCopyFn(tLab, tChat);
  const api = useChatToolbarStore((s) => s.api);

  const modelSelectId = useId();
  const classifierSelectId = useId();
  const presetSelectId = useId();
  const uploadInputRef = useRef<HTMLInputElement>(null);

  const [editOpen, setEditOpen] = useState(false);
  const [showIncompatiblePresets, setShowIncompatiblePresets] = useState(false);
  const [advancedError, setAdvancedError] = useState<string | null>(null);
  const [advancedValidationText, setAdvancedValidationText] = useState<string | null>(null);

  const capabilitiesQuery = useRuntimeConfigCapabilities();
  const effectiveEmbeddingDefaults = useMeEffectiveEmbeddingDefaults();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;
  const hasCustomOverride =
    api?.runtimeState?.configurationMode === "CUSTOM" || Boolean(api?.runtimeState?.isCustom);

  const effectiveRetrieval = api?.runtimeState?.effectiveRetrievalParameters ?? null;
  const effectiveConfig = api?.runtimeState?.effectiveConfig ?? null;
  const activeLlmModel = useMemo(() => {
    if (effectiveConfig && typeof effectiveConfig === "object") {
      const model = (effectiveConfig as Record<string, unknown>).llmModel;
      if (typeof model === "string" && model.trim()) return model.trim();
    }
    return api?.runtimeState?.conversationLlmModel?.trim() ?? "";
  }, [effectiveConfig, api?.runtimeState?.conversationLlmModel]);
  const indexProfileQuery = useProjectIndexProfile(api?.projectId);
  const activeSnapQuery = useActiveProjectSnapshot(api?.projectId);

  const effectiveError = api?.runtimeStateError ?? null;
  const patchPending = Boolean(api?.patchConvPending);

  const selectedLlmModel = activeLlmModel;
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
  const normalWarningIssues = useMemo(
    () => warningIssues.filter((issue) => !isAdvancedTechnicalValidationIssue(issue)),
    [warningIssues],
  );
  const advancedTechnicalWarningIssues = useMemo(
    () => warningIssues.filter((issue) => isAdvancedTechnicalValidationIssue(issue)),
    [warningIssues],
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
    const m = new Map<string, DisabledRuntimeFeatureDto>();
    for (const item of api?.runtimeState?.disabledRuntimeFeatures ?? []) {
      if (item.key) m.set(item.key, item);
    }
    return m;
  }, [api?.runtimeState?.disabledRuntimeFeatures]);

  const classifierModelsQuery = useClassifierModelsQuery(Boolean(api?.projectId));

  const runtimeToggles = useMemo(() => {
    const filtered = caps.filter(
      (c) =>
        (c.category === "RUNTIME_HOT_SWAPPABLE" || c.category === "ADVANCED_RUNTIME") &&
        c.visibleInChat === true &&
        c.configurableInChat === true &&
        c.implemented === true &&
        c.engineWired === true,
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

  const presetBaseEffectiveConfig = useMemo(
    () =>
      api?.runtimeState?.baseEffectiveConfig && typeof api.runtimeState.baseEffectiveConfig === "object"
        ? (api.runtimeState.baseEffectiveConfig as Record<string, unknown>)
        : null,
    [api],
  );

  const disabledTip = (key: string): string | null => {
    const backendDisabled = disabledRuntimeFeatureByKey.get(key);
    if (backendDisabled) {
      const tip = formatDisabledRuntimeFeatureTip(backendDisabled, tChat);
      if (tip) return tip;
    }
    const cap = capByKey.get(key);
    const clientTip = clientSideDisableTip(key, cap, mergedRuntimeFlagValues, tChat);
    if (clientTip) return clientTip;
    if (isPresetBaseFeature(key, presetBaseEffectiveConfig)) {
      const tip = tChat("chatFeatureTipEnabledByPreset");
      return tip !== "chatFeatureTipEnabledByPreset" ? tip : null;
    }
    if (isPresetControlledOffFeature(key, presetBaseEffectiveConfig)) {
      const tip = tChat("chatFeatureTipPresetControlled");
      return tip !== "chatFeatureTipPresetControlled" ? tip : null;
    }
    if (cap?.visibleInChat === true && (!cap.configurableInChat || !cap.implemented || !cap.engineWired)) {
      const unavailable = tChat("chatFeatureTipNotAvailableInChat");
      return unavailable !== "chatFeatureTipNotAvailableInChat" ? unavailable : null;
    }
    return null;
  };

  const isToggleDisabled = (key: string): boolean => {
    if (patchPending) return true;
    if (disabledTip(key) != null) return true;
    if (isPresetBaseFeature(key, presetBaseEffectiveConfig)) return true;
    if (isPresetControlledOffFeature(key, presetBaseEffectiveConfig)) return true;
    const cap = capByKey.get(key);
    if (!cap || cap.visibleInChat !== true) return false;
    if (!cap.configurableInChat || !cap.implemented || !cap.engineWired) return true;
    for (const reqKey of cap.requires ?? []) {
      if (!coerceBool(mergedRuntimeFlagValues[reqKey])) return true;
    }
    for (const exKey of cap.excludes ?? []) {
      if (coerceBool(mergedRuntimeFlagValues[exKey])) return true;
    }
    if (
      key === "metadataEnabled" &&
      coerceBool(mergedRuntimeFlagValues.metadataEnabled) &&
      !coerceBool(mergedRuntimeFlagValues.toolsEnabled)
    ) {
      return true;
    }
    return false;
  };

  function presetFeatureBadge(key: string): ReactNode {
    if (!isPresetBaseFeature(key, presetBaseEffectiveConfig)) return null;
    return (
      <span
        className="shrink-0 rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium"
        data-testid={`chat-runtime-preset-badge-${key}`}
      >
        {tChat("chatFeatureTipEnabledByPreset")}
      </span>
    );
  }

  function hybridMaterializationHint(key: string): string | null {
    if (key !== "rankerEnabled" && key !== "postRetrievalEnabled") return null;
    if (!coerceBool(mergedRuntimeFlagValues.useRetrieval)) return null;
    const activeMat =
      indexProfileQuery.data?.materializationStrategy ??
      activeSnapshotCapabilities?.materializationStrategy ??
      null;
    if (!activeMat || activeMat === "HYBRID") return null;
    return key === "rankerEnabled"
      ? tChat("chatConfigRankerRequiresHybrid")
      : tChat("chatConfigPostRetrievalRequiresHybrid");
  }

  const runtimeSelectedPresetId = api?.runtimeState?.selectedPresetId ?? null;
  const effectivePresetId = api?.runtimeState?.effectivePresetId ?? null;
  const selectedPresetValue = runtimeSelectedPresetId ?? effectivePresetId ?? "";
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

  useEffect(() => {
    const known = new Set<string>([
      ...RETRIEVAL_SETTING_KEYS,
      ...ADVANCED_RUNTIME_ONLY_KEYS,
      MEMORY_FEATURE_KEY,
      CLARIFICATION_FEATURE_KEY,
      ANSWER_QUALITY_FEATURE_KEY,
      "expansionEnabled",
    ]);
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

  const effectivePromptSource = useMemo(() => {
    const customSystem =
      typeof mergedRuntimeFlagValues.llmSystemPrompt === "string"
      && mergedRuntimeFlagValues.llmSystemPrompt.trim().length > 0;
    const overrides = mergedRuntimeFlagValues.promptOverrides;
    const customInternal =
      overrides != null
      && typeof overrides === "object"
      && !Array.isArray(overrides)
      && Object.values(overrides as Record<string, unknown>).some(
        (v) => typeof v === "string" && v.trim().length > 0,
      );
    return customSystem || customInternal ? "project" : "default";
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
  const productPresetCompatibility = useMemo(
    () => compatibilityByProductPresetId(api?.compatibleProductPresets),
    [api?.compatibleProductPresets],
  );
  const experimentalPresetCompatibility = useMemo(
    () => compatibilityByExperimentalPresetId(api?.compatibleExperimentalPresets),
    [api?.compatibleExperimentalPresets],
  );
  const projectIndexCaps = useMemo(
    () => api?.projectCompatiblePresets?.activeSnapshotCapabilities ?? null,
    [api?.projectCompatiblePresets?.activeSnapshotCapabilities],
  );
  const visibleProductPresets = useMemo(
    () =>
      filterProductPresetsForSelector(
        api?.compatibleProductPresets,
        projectIndexCaps,
        showIncompatiblePresets,
      ),
    [api?.compatibleProductPresets, projectIndexCaps, showIncompatiblePresets],
  );
  const visibleExperimentalItems = useMemo(
    () =>
      filterExperimentalPresetsForSelector(
        api?.compatibleExperimentalPresets,
        projectIndexCaps,
        showIncompatiblePresets,
      ).filter((item) => !productIds.has(item.preset.productPresetId)),
    [
      api?.compatibleExperimentalPresets,
      projectIndexCaps,
      showIncompatiblePresets,
      productIds,
    ],
  );
  const experimentalUnique = useMemo(
    () => sortPresetsByRank(visibleExperimentalItems.map((item) => item.preset)),
    [visibleExperimentalItems],
  );
  const presetEmptyState = useMemo(
    () =>
      projectCompatiblePresetsEmptyState(
        api?.projectCompatiblePresets ?? null,
        showIncompatiblePresets,
        projectIndexCaps,
      ),
    [api?.projectCompatiblePresets, showIncompatiblePresets, projectIndexCaps],
  );
  const isStructuredSearchProject =
    (projectIndexCaps?.materializationStrategy ?? "").trim().toUpperCase() === "STRUCTURED_SEARCH";

  function presetIndexDisabledReason(p: {
    chatSelectable: boolean;
    supportStatus: string;
    reasonIfUnsupported: string | null;
    indexRequirements?: {
      requiredMaterializationStrategy: string | null;
      requiresMetadataSupport: boolean;
    } | null;
    productPresetId?: string;
    id?: string;
  }): string | null {
    const backendReason =
      (p.productPresetId
        ? experimentalPresetCompatibility.get(p.productPresetId)
        : p.id
          ? productPresetCompatibility.get(p.id)
          : undefined) ?? null;
    const fromBackend = presetCompatibilityDisabledReason(backendReason);
    if (fromBackend) {
      return formatPresetCompatibilityDisabledReason(backendReason, tChat) ?? fromBackend;
    }

    if (!p.chatSelectable) {
      return formatPresetSupportMessage(p.supportStatus, p.reasonIfUnsupported, presetCopyT, "chatPresetNotSelectable");
    }
    const req = p.indexRequirements;
    if (!req) return null;
    const idx = api?.runtimeState?.indexCompatibility;
    const caps = idx?.activeSnapshotCapabilities;
    if (!idx?.hasActiveIndex || !caps) {
      return tChat("chatPresetCompatNoActiveIndex");
    }
    const activeMat = caps.materializationStrategy;
    const requiredMat = req.requiredMaterializationStrategy;
    const matOk =
      !requiredMat ||
      activeMat === requiredMat ||
      (activeMat === "HYBRID" && requiredMat === "CHUNK_LEVEL");
    if (!matOk) {
      if (activeMat === "STRUCTURED_SEARCH") {
        return tChat("chatPresetCompatStructuredSearchNoRetrieval");
      }
      if (requiredMat === "DOCUMENT_LEVEL") return tChat("chatPresetCompatRequiresDocumentLevel");
      if (requiredMat === "CHUNK_LEVEL") return tChat("chatPresetCompatRequiresChunkLevel");
      if (requiredMat === "HYBRID") return tChat("chatPresetCompatRequiresHybrid");
      return tChat("chatPresetRequiresCompatibleIndex");
    }
    if (req.requiresMetadataSupport && caps.supportsMetadata !== true) {
      return tChat("chatPresetCompatRequiresMetadata");
    }
    return null;
  }

  const getBooleanValue = (key: string): boolean => {
    if (api?.runtimeState?.effectiveConfig) {
      return coerceBool((api.runtimeState.effectiveConfig as Record<string, unknown>)[key]);
    }
    return coerceBool(effectiveConfig ? (effectiveConfig as Record<string, unknown>)[key] : undefined);
  };

  const assistantRetrievalDefaults = useMemo(
    () => toRetrievalDefaults(effectiveEmbeddingDefaults.data?.retrievalOptions),
    [effectiveEmbeddingDefaults.data?.retrievalOptions],
  );

  const projectStoredRagConfigQuery = useProjectStoredRagConfigQuery(api?.projectId);
  const projectRetrievalDefaults = useMemo(
    () => toRetrievalDefaults(projectStoredRagConfigQuery.data),
    [projectStoredRagConfigQuery.data],
  );

  const persistedRuntimeOverride = useMemo(
    () => (api?.runtimeState?.runtimeOverride ?? api?.runtimeOverride ?? {}) as Record<string, unknown>,
    [api?.runtimeState?.runtimeOverride, api?.runtimeOverride],
  );

  const conversationConfigDraft = useConversationConfigurationDraft(
    persistedRuntimeOverride,
    api?.conversationId,
    patchPending,
  );

  const [pendingRetrievalMode, setPendingRetrievalMode] = useState<RetrievalOverrideMode | null>(null);

  useEffect(() => {
    if (!patchPending) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clear optimistic mode after PATCH settles
      setPendingRetrievalMode(null);
    }
  }, [patchPending, persistedRuntimeOverride, api?.conversationId]);

  const saveConversationPatch = (patch: Record<string, unknown>) => {
    if (!api?.saveRuntimeOverride) {
      return;
    }
    api.saveRuntimeOverride(
      sanitizeRuntimeOverridePatch(patch, getBooleanValue("useRetrieval")),
    );
  };

  const setOverrideBoolean = (key: string, next: boolean) => {
    saveConversationPatch(conversationConfigDraft.applyBooleanPatch(key, next));
  };

  const getNumberValue = (key: string, fallback: number): number => {
    const value = mergedRuntimeFlagValues[key];
    return typeof value === "number" && Number.isFinite(value) ? value : fallback;
  };

  const retrievalOverrideMode = useMemo(() => {
    if (pendingRetrievalMode) {
      return pendingRetrievalMode;
    }
    return inferRetrievalOverrideMode(
      persistedRuntimeOverride,
      assistantRetrievalDefaults,
      projectRetrievalDefaults,
    );
  }, [
    assistantRetrievalDefaults,
    pendingRetrievalMode,
    persistedRuntimeOverride,
    projectRetrievalDefaults,
  ]);

  const committedTopK = getNumberValue("topK", effectiveRetrieval?.topK ?? 12);
  const committedSimilarity = getNumberValue(
    "similarityThreshold",
    effectiveRetrieval?.similarityThreshold ?? 0,
  );

  const ensureCustomRetrievalMode = () => {
    const snapshot = conversationConfigDraft.getSnapshot();
    if (snapshot[RETRIEVAL_OVERRIDE_MODE_KEY] === "custom") {
      return;
    }
    const seed =
      effectiveRetrieval != null
        ? { topK: effectiveRetrieval.topK, similarityThreshold: effectiveRetrieval.similarityThreshold }
        : assistantRetrievalDefaults;
    saveConversationPatch(
      conversationConfigDraft.applyPatch(
        seed
          ? {
              [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
              topK: seed.topK,
              similarityThreshold: seed.similarityThreshold,
            }
          : { [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom" },
      ),
    );
  };

  const commitRetrievalTopK = (next: number) => {
    saveConversationPatch(
      conversationConfigDraft.applyPatch({
        [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
        topK: next,
        similarityThreshold: committedSimilarity,
      }),
    );
  };

  const commitRetrievalSimilarity = (next: number) => {
    saveConversationPatch(
      conversationConfigDraft.applyPatch({
        [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
        topK: committedTopK,
        similarityThreshold: next,
      }),
    );
  };

  const retrievalFieldDrafts = useRetrievalFieldDrafts({
    committedTopK,
    committedSimilarity,
    retrievalOverrideMode,
    conversationId: api?.conversationId,
    patchPending,
    topKConstraints: RETRIEVAL_TOP_K_CONSTRAINTS,
    similarityConstraints: RETRIEVAL_SIMILARITY_CONSTRAINTS,
    onEnsureCustomMode: ensureCustomRetrievalMode,
    onCommitTopK: commitRetrievalTopK,
    onCommitSimilarity: commitRetrievalSimilarity,
  });

  const setRetrievalOverrideMode = (mode: RetrievalOverrideMode) => {
    setPendingRetrievalMode(mode);
    const seed =
      effectiveRetrieval != null
        ? { topK: effectiveRetrieval.topK, similarityThreshold: effectiveRetrieval.similarityThreshold }
        : projectRetrievalDefaults ?? assistantRetrievalDefaults;
    saveConversationPatch(
      conversationConfigDraft.applyPatch(
        buildRetrievalModePatch(
          mode,
          conversationConfigDraft.getSnapshot(),
          seed,
          assistantRetrievalDefaults,
        ),
      ),
    );
  };

  const retrievalFieldsEditable = retrievalFieldDrafts.displayRetrievalMode === "custom";

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

  const displayModelLabel = activeLlmModel.trim() || tChat("modelDefault");
  const conversationLlmOverride = api?.runtimeState?.conversationLlmModel?.trim() ?? "";
  const finalAnswerModelSource = conversationLlmOverride
    ? tChat("chatFinalAnswerModelSourceConversationOverride")
    : tChat("chatFinalAnswerModelSourceAssistantConfiguration");
  const retrievalEnabled = getBooleanValue("useRetrieval");
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
    <div
      className="@container/chat-config flex min-w-0 max-w-full flex-col gap-6 break-words [overflow-wrap:anywhere]"
      data-testid="chat-assistant-configuration-surface"
    >
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
                <li key={`${issue.code}-${issue.field ?? "global"}`} data-testid="chat-runtime-blocking-issue">
                  {formatRuntimeValidationIssueMessage(issue, tChat)}
                </li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}

      {isStructuredSearchProject ? (
        <p
          className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100"
          data-testid="chat-structured-search-legacy-warning"
          role="status"
        >
          {tChat("structuredSearchLegacyProjectWarning")}
        </p>
      ) : null}

      {normalWarningIssues.length > 0 ? (
        <div
          className="rounded-lg border bg-muted/30 px-3 py-2 text-xs text-muted-foreground"
          role="status"
          data-testid="chat-config-validation-warning"
        >
          {formatRuntimeValidationIssueMessage(normalWarningIssues[0]!, tChat)}
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
                <span className="text-muted-foreground ml-1 text-[11px]" data-testid="chat-config-summary-model-source">
                  ({finalAnswerModelSource})
                </span>
              </span>
            }
          />
          <CompactSummaryRow
            label={tChat("configCompactPreset")}
            value={
              <span data-testid="chat-config-summary-preset">
                {displayPresetLabel}
                {presetKindBadge ? (
                  <span className="text-muted-foreground ml-1 text-[11px]">({presetKindBadge})</span>
                ) : null}
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
            label={tChat("configCompactDocSearch")}
            value={
              <span data-testid="chat-config-summary-doc-search">
                {retrievalEnabled ? tChat("configCompactDocSearchOn") : tChat("configCompactDocSearchOff")}
              </span>
            }
          />
          {retrievalEnabled && effectiveRetrieval ? (
            <CompactSummaryRow
              label={tChat("configSectionRetrievalSettings")}
              value={
                <span data-testid="chat-config-summary-retrieval">
                  {tChat("configRetrievalTopKLabel")}: {effectiveRetrieval.topK} (
                  {tChat(retrievalParameterSourceLabelKey(effectiveRetrieval.topKSource))}) ·{" "}
                  {tChat("configRetrievalSimilarityLabel")}: {effectiveRetrieval.similarityThreshold} (
                  {tChat(retrievalParameterSourceLabelKey(effectiveRetrieval.similarityThresholdSource))})
                </span>
              }
            />
          ) : null}
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
            onClick={() => setEditOpen((open) => !open)}
          >
            {editOpen ? tChat("configEditClose") : tChat("configEditButton")}
          </button>
        </div>
      </div>

      {editOpen ? (
        <>
      <p className="text-muted-foreground text-xs" data-testid="chat-config-scope-legend">
        {tChat("configScopeLegend")}
      </p>

      <Section title={tChat("configSectionAssistant")}>
        <Box>
          <div className="space-y-4">
            <div className="space-y-3" data-testid="chat-assistant-document-scope">
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

            <div className="space-y-3 border-t pt-3" data-testid="chat-assistant-preset-section">
              <div className="flex flex-col gap-1">
                <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <Label htmlFor={presetSelectId} className="shrink-0 text-xs">
                    {tChat("presetLabel")}
                  </Label>
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
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
                        {tChat("conversationConfigurationModeCustom")}
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

                <div className="flex flex-col gap-2">
                  <label className="flex items-center gap-2 text-[11px] text-muted-foreground">
                    <input
                      type="checkbox"
                      data-testid="chat-preset-show-incompatible"
                      checked={showIncompatiblePresets}
                      onChange={(e) => setShowIncompatiblePresets(e.target.checked)}
                      disabled={needsProject || needsConversation}
                    />
                    {tChat("presetShowIncompatible")}
                  </label>
                </div>

                {isStructuredSearchProject ? (
                  <p
                    className="text-muted-foreground text-xs"
                    data-testid="chat-preset-structured-search-warning"
                  >
                    {tChat("structuredSearchLegacyProjectWarning")}
                  </p>
                ) : null}

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
                  {runtimeSelectedPresetId &&
                  !selectedInProduct &&
                  !selectedExperimental &&
                  api?.runtimeState?.preset?.label ? (
                    <option value={runtimeSelectedPresetId}>
                      {toProductPresetDisplayName(api.runtimeState.preset.label.trim())}
                    </option>
                  ) : null}
                  <optgroup label={tChat("presetGroupProduct")}>
                    {visibleProductPresets.map((item) => {
                      const reason = presetCompatibilityDisabledReason(item.compatibility);
                      const baselineLabel = presetProductTierLabel();
                      const optionLabel =
                        reason != null
                          ? `${formatProductPresetOptionLabel(item.preset, tChat)} (${reason})`
                          : baselineLabel
                            ? `${formatProductPresetOptionLabel(item.preset, tChat)} — ${baselineLabel}`
                            : formatProductPresetOptionLabel(item.preset, tChat);
                      const optionTitle = formatProductPresetOptionTitle(item.preset, tChat);
                      return (
                        <option
                          key={item.preset.id}
                          value={item.preset.id}
                          disabled={Boolean(reason)}
                          title={reason ?? optionTitle}
                        >
                          {optionLabel}
                        </option>
                      );
                    })}
                  </optgroup>
                  <optgroup label={tChat("presetGroupExperimental")}>
                    {experimentalUnique.map((p) => {
                      const reason = presetIndexDisabledReason({ ...p, productPresetId: p.productPresetId });
                      const baselineLabel = presetProductTierLabel();
                      const optionLabel = reason
                        ? `${formatChatExperimentalPresetOptionLabel(p, presetCopyT)} (${reason})`
                        : baselineLabel
                          ? `${formatChatExperimentalPresetOptionLabel(p, presetCopyT)} — ${baselineLabel}`
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

                {presetEmptyState === "no-index" ? (
                  <output className="text-muted-foreground text-xs" data-testid="chat-preset-empty-state">
                    {tChat("presetNoIndexEmpty")}
                  </output>
                ) : null}
                {presetEmptyState === "no-compatible" ? (
                  <output className="text-muted-foreground text-xs" data-testid="chat-preset-empty-state">
                    {tChat("presetNoCompatibleAvailable")}
                  </output>
                ) : null}

                {selectedPresetDisabledReason ? (
                  <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-xs">
                    <p className="break-words font-medium text-destructive">{selectedPresetDisabledReason}</p>
                    {api?.runtimeState?.requiresReindex ? (
                      <p className="text-muted-foreground mt-1 break-words" data-testid="chat-preset-incompatible-fixed-index-hint">
                        {tChat("chatPresetIncompatibleFixedIndexHint")}
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
          </div>
        </Box>
      </Section>

      <Section title={tChat("configSectionModels")}>
        <Box>
          <div className="space-y-3">
            <div className="flex flex-col gap-2" data-testid="chat-llm-configuration-hint">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <MenuHint>{tChat("chatLlmModelsAssistantConfigurationHint")}</MenuHint>
                <Link
                  href="/settings/user"
                  className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                >
                  <span data-testid="chat-edit-assistant-configuration-link">
                    {tChat("chatOpenAssistantConfigurationAction")}
                  </span>
                </Link>
              </div>
              <CompactSummaryRow label={tChat("modelLabel")} value={displayModelLabel} />
            </div>

            <div className="flex flex-col gap-1">
              <LabelWithScopeBadge
                label={
                  <Label htmlFor={classifierSelectId} className="text-xs">
                    {tChat("classifierLabel")}
                  </Label>
                }
                scope={scopeForRuntimeKey("classifierModelId")}
                scopeLabelText={scopeLabel(scopeForRuntimeKey("classifierModelId"))}
              />
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

            <div
              className="flex flex-col gap-1 border-t pt-3"
              data-testid="chat-final-answer-model-override"
            >
              <LabelWithScopeBadge
                label={
                  <Label htmlFor={modelSelectId} className="text-xs">
                    {tChat("chatFinalAnswerModelOverrideLabel")}
                  </Label>
                }
                scope="conversation"
                scopeLabelText={scopeLabel("conversation")}
              />
              <MenuHint>{tChat("chatFinalAnswerModelOverrideHint")}</MenuHint>
              <select
                id={modelSelectId}
                data-testid="chat-final-answer-model-select"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  (api?.modelsError || llmSelectionInvalid) && "border-destructive",
                )}
                value={conversationLlmOverride}
                onChange={(e) => api?.setLlmModelChoice(e.target.value)}
                disabled={needsProject || needsConversation || patchPending || api?.selectableLlmModelsLoading}
                aria-label={tChat("chatFinalAnswerModelOverrideLabel")}
              >
                <option value="">{tChat("chatFinalAnswerModelOverrideDefault")}</option>
                {(api?.selectableLlmModels ?? []).map((m) => (
                  <option key={m.modelName} value={m.modelName}>
                    {m.modelName}
                    {llmProviderLabel ? ` (${llmProviderLabel})` : ""}
                  </option>
                ))}
              </select>
              {llmSelectionInvalid ? (
                <output className="text-destructive text-xs">{tChat("chatFinalAnswerModelOverrideInvalid")}</output>
              ) : null}
            </div>

            {embeddingIndexBoundCap ? (
              <div className="flex min-w-0 flex-col gap-1 border-t pt-3" data-testid="chat-embedding-model-readonly">
                <LabelWithScopeBadge
                  label={<Label className="text-xs">{tChat("chatEmbeddingModelLabel")}</Label>}
                  scope="project"
                  scopeLabelText={scopeLabel("project")}
                />
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
            ) : null}
          </div>
        </Box>
      </Section>


      <Section title={tChat("configSectionPrompts")}>
        <Box>
          <div className="space-y-2" data-testid="chat-assistant-instructions-section">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <MenuHint>{tChat("chatPromptConfigSummary")}</MenuHint>
              {api?.projectId ? (
                <Link
                  href="/settings/project#internal-prompt-configuration"
                  className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                  data-testid="chat-edit-project-prompts-link"
                >
                  {tChat("chatConfigureProjectPromptsAction")}
                </Link>
              ) : null}
            </div>
            <p className="text-muted-foreground text-xs" data-testid="chat-effective-prompt-source">
              {tChat("chatEffectivePromptSourceLabel")}:{" "}
              {effectivePromptSource === "project"
                ? tChat("chatEffectivePromptSourceProject")
                : tChat("chatEffectivePromptSourceDefault")}
            </p>
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

      <Section title={tChat("configSectionRetrievalSettings")}>
        <Box>
          <div className="space-y-4" data-testid="chat-retrieval-settings-section">
            {!retrievalEnabled ? (
              <p
                className="text-muted-foreground text-xs"
                data-testid="chat-retrieval-settings-not-applicable"
                role="status"
              >
                {tChat("chatRetrievalSettingsNotApplicable")}
              </p>
            ) : (
              <>
            <fieldset className="space-y-2 rounded-md border bg-background/50 p-3" data-testid="chat-retrieval-override-mode">
              <legend className="px-1 text-xs font-medium">{tChat("retrievalOverrideModeLegend")}</legend>
              <label className="flex min-w-0 cursor-pointer items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="chat-retrieval-override-mode"
                  className="mt-0.5 shrink-0"
                  data-testid="chat-retrieval-mode-preset"
                  checked={retrievalFieldDrafts.displayRetrievalMode === "preset"}
                  disabled={patchPending}
                  onChange={() => setRetrievalOverrideMode("preset")}
                />
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">
                  {tChat("retrievalOverrideModePreset")}
                </span>
              </label>
              <label className="flex min-w-0 cursor-pointer items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="chat-retrieval-override-mode"
                  className="mt-0.5 shrink-0"
                  data-testid="chat-retrieval-mode-project-settings"
                  checked={retrievalFieldDrafts.displayRetrievalMode === "project_settings"}
                  disabled={patchPending}
                  onChange={() => setRetrievalOverrideMode("project_settings")}
                />
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">
                  {tChat("retrievalOverrideModeProjectSettings")}
                </span>
              </label>
              <label className="flex min-w-0 cursor-pointer items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="chat-retrieval-override-mode"
                  className="mt-0.5 shrink-0"
                  data-testid="chat-retrieval-mode-assistant-defaults"
                  checked={retrievalFieldDrafts.displayRetrievalMode === "assistant_defaults"}
                  disabled={patchPending || !assistantRetrievalDefaults}
                  onChange={() => setRetrievalOverrideMode("assistant_defaults")}
                />
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">
                  {tChat("retrievalOverrideModeAssistantDefaults")}
                </span>
              </label>
              <label className="flex min-w-0 cursor-pointer items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="chat-retrieval-override-mode"
                  className="mt-0.5 shrink-0"
                  data-testid="chat-retrieval-mode-custom"
                  checked={retrievalFieldDrafts.displayRetrievalMode === "custom"}
                  disabled={patchPending}
                  onChange={() => setRetrievalOverrideMode("custom")}
                />
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">
                  {tChat("retrievalOverrideModeCustom")}
                </span>
              </label>
            </fieldset>
            <div className="flex flex-wrap gap-3">
              <RetrievalNumericField
                testId="chat-runtime-toggle-topK"
                inputMode="numeric"
                disabled={patchPending || !retrievalFieldsEditable}
                draft={retrievalFieldDrafts.topKDraft}
                focused={retrievalFieldDrafts.topKFocused}
                touched={retrievalFieldDrafts.topKTouched}
                onFocus={retrievalFieldDrafts.handleTopKFocus}
                onBlur={retrievalFieldDrafts.handleTopKBlur}
                onDraftChange={retrievalFieldDrafts.handleTopKChange}
                errorMessages={{
                  invalid: tChat("retrievalTopKInvalid"),
                  range: tChat("retrievalTopKRange", RETRIEVAL_TOP_K_CONSTRAINTS),
                  required: tChat("retrievalTopKRequired"),
                }}
                label={
                  <LabelWithScopeBadge
                    label={tChat("configRetrievalTopKLabel")}
                    scope={scopeForRuntimeKey("topK")}
                    scopeLabelText={scopeLabel(scopeForRuntimeKey("topK"))}
                  />
                }
                sourceHint={
                  effectiveRetrieval ? (
                    <span className="text-muted-foreground text-[11px]" data-testid="chat-retrieval-topk-source">
                      {tChat(retrievalParameterSourceLabelKey(effectiveRetrieval.topKSource))}
                    </span>
                  ) : null
                }
              />
              <RetrievalNumericField
                testId="chat-runtime-toggle-similarityThreshold"
                inputMode="decimal"
                disabled={patchPending || !retrievalFieldsEditable}
                draft={retrievalFieldDrafts.similarityDraft}
                focused={retrievalFieldDrafts.similarityFocused}
                touched={retrievalFieldDrafts.similarityTouched}
                onFocus={retrievalFieldDrafts.handleSimilarityFocus}
                onBlur={retrievalFieldDrafts.handleSimilarityBlur}
                onDraftChange={retrievalFieldDrafts.handleSimilarityChange}
                errorMessages={{
                  invalid: tChat("retrievalSimilarityInvalid"),
                  range: tChat("retrievalSimilarityRange", RETRIEVAL_SIMILARITY_CONSTRAINTS),
                  required: tChat("retrievalSimilarityRequired"),
                }}
                label={
                  <LabelWithScopeBadge
                    label={tChat("configRetrievalSimilarityLabel")}
                    scope={scopeForRuntimeKey("similarityThreshold")}
                    scopeLabelText={scopeLabel(scopeForRuntimeKey("similarityThreshold"))}
                  />
                }
                sourceHint={
                  effectiveRetrieval ? (
                    <span className="text-muted-foreground text-[11px]" data-testid="chat-retrieval-threshold-source">
                      {tChat(retrievalParameterSourceLabelKey(effectiveRetrieval.similarityThresholdSource))}
                    </span>
                  ) : null
                }
              />
            </div>
              </>
            )}
            <div className="grid grid-cols-1 gap-3">
              {retrievalRuntimeToggles.map((cap) => {
                const key = cap.key;
                const tip = disabledTip(key);
                const hybridHint = hybridMaterializationHint(key);
                const labelKey = chatRuntimeLabelKey(key);
                const label = tChat(labelKey) !== labelKey ? tChat(labelKey) : cap.label ?? key;
                return (
                  <div key={key} className="flex flex-col gap-1">
                    <RuntimeCheckboxFeatureRow
                      testId={`chat-runtime-toggle-${key}`}
                      checked={getBooleanValue(key)}
                      disabled={isToggleDisabled(key)}
                      onChange={(next) => setOverrideBoolean(key, next)}
                      label={label}
                      scope={scopeForRuntimeKey(key)}
                      scopeLabelText={scopeLabel(scopeForRuntimeKey(key))}
                      presetBadge={presetFeatureBadge(key)}
                      disabledReasonNode={
                        tip ? (
                          <FeatureDisableTip tip={tip} testId={`chat-runtime-disable-tip-${key}`} />
                        ) : null
                      }
                    />
                    {hybridHint ? (
                      <span className="pl-6" data-testid={`chat-runtime-hybrid-hint-${key}`}>
                        <MenuHint>{hybridHint}</MenuHint>
                      </span>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </div>
        </Box>
      </Section>

      {(memoryRuntimeCap || clarificationRuntimeCap) ? (
        <Section title={tChat("configSectionMemoryAndClarification")}>
          <Box className="space-y-4" data-testid="chat-memory-clarification-section">
            {memoryRuntimeCap ? (() => {
              const memoryTip = disabledTip(MEMORY_FEATURE_KEY);
              return (
                <div data-testid="chat-conversation-memory-section">
                  <RuntimeCheckboxFeatureRow
                    testId="chat-runtime-toggle-memoryEnabled"
                    checked={getBooleanValue(MEMORY_FEATURE_KEY)}
                    disabled={isToggleDisabled(MEMORY_FEATURE_KEY)}
                    onChange={(next) => setOverrideBoolean(MEMORY_FEATURE_KEY, next)}
                    label={tChat("runtimeFeatureMemoryEnabled")}
                    scope={scopeForRuntimeKey(MEMORY_FEATURE_KEY)}
                    scopeLabelText={scopeLabel(scopeForRuntimeKey(MEMORY_FEATURE_KEY))}
                    presetBadge={presetFeatureBadge(MEMORY_FEATURE_KEY)}
                    multiTurn={memoryRuntimeCap.supportMode === "MULTI_TURN_REQUIRED"}
                    multiTurnHint={
                      memoryRuntimeCap.supportMode === "MULTI_TURN_REQUIRED"
                        ? tChat("runtimeMultiTurnHint")
                        : undefined
                    }
                    disabledReasonNode={
                      memoryTip ? (
                        <FeatureDisableTip tip={memoryTip} testId="chat-runtime-disable-tip-memoryEnabled" />
                      ) : null
                    }
                  />
                </div>
              );
            })() : null}
            {clarificationRuntimeCap ? (() => {
              const clarificationTip = disabledTip(CLARIFICATION_FEATURE_KEY);
              return (
                <div data-testid="chat-clarification-section">
                  <RuntimeCheckboxFeatureRow
                    testId="chat-runtime-toggle-clarificationEnabled"
                    checked={getBooleanValue(CLARIFICATION_FEATURE_KEY)}
                    disabled={isToggleDisabled(CLARIFICATION_FEATURE_KEY)}
                    onChange={(next) => setOverrideBoolean(CLARIFICATION_FEATURE_KEY, next)}
                    label={tChat("runtimeFeatureClarificationEnabled")}
                    scope={scopeForRuntimeKey(CLARIFICATION_FEATURE_KEY)}
                    scopeLabelText={scopeLabel(scopeForRuntimeKey(CLARIFICATION_FEATURE_KEY))}
                    presetBadge={presetFeatureBadge(CLARIFICATION_FEATURE_KEY)}
                    multiTurn={clarificationRuntimeCap.supportMode === "MULTI_TURN_REQUIRED"}
                    multiTurnHint={
                      clarificationRuntimeCap.supportMode === "MULTI_TURN_REQUIRED"
                        ? tChat("runtimeMultiTurnHint")
                        : undefined
                    }
                    disabledReasonNode={
                      clarificationTip ? (
                        <FeatureDisableTip
                          tip={clarificationTip}
                          testId="chat-runtime-disable-tip-clarificationEnabled"
                        />
                      ) : null
                    }
                  />
                </div>
              );
            })() : null}
          </Box>
        </Section>
      ) : null}

      {(answerQualityRuntimeCap || advancedRuntimeToggles.length > 0 || editOpen) ? (
        <Section title={tChat("configSectionToolsAndQualityChecks")}>
          <Box className="space-y-4">
            {answerQualityRuntimeCap ? (
              <div data-testid="chat-answer-quality-section">
                <RuntimeCheckboxFeatureRow
                  testId="chat-runtime-toggle-judgeEnabled"
                  checked={getBooleanValue(ANSWER_QUALITY_FEATURE_KEY)}
                  disabled={isToggleDisabled(ANSWER_QUALITY_FEATURE_KEY)}
                  onChange={(next) => setOverrideBoolean(ANSWER_QUALITY_FEATURE_KEY, next)}
                  label={tChat("runtimeFeatureAnswerQualityChecks")}
                  scope={scopeForRuntimeKey(ANSWER_QUALITY_FEATURE_KEY)}
                  scopeLabelText={scopeLabel(scopeForRuntimeKey(ANSWER_QUALITY_FEATURE_KEY))}
                  presetBadge={presetFeatureBadge(ANSWER_QUALITY_FEATURE_KEY)}
                />
              </div>
            ) : null}

            <div className="grid grid-cols-1 gap-3">
              {advancedRuntimeToggles.map((cap) => {
                const key = cap.key;
                const tip = disabledTip(key);
                const showMultiTurn = cap.supportMode === "MULTI_TURN_REQUIRED";
                const labelKey = chatRuntimeLabelKey(key);
                const label = tChat(labelKey) !== labelKey ? tChat(labelKey) : cap.label ?? key;
                return (
                  <RuntimeCheckboxFeatureRow
                    key={key}
                    testId={`chat-runtime-toggle-${key}`}
                    checked={getBooleanValue(key)}
                    disabled={isToggleDisabled(key)}
                    onChange={(next) => setOverrideBoolean(key, next)}
                    label={label}
                    scope={scopeForRuntimeKey(key)}
                    scopeLabelText={scopeLabel(scopeForRuntimeKey(key))}
                    presetBadge={presetFeatureBadge(key)}
                    multiTurn={showMultiTurn}
                    multiTurnHint={showMultiTurn ? tChat("runtimeMultiTurnHint") : undefined}
                    disabledReasonNode={
                      tip ? (
                        <FeatureDisableTip tip={tip} testId={`chat-runtime-disable-tip-${key}`} />
                      ) : null
                    }
                  />
                );
              })}
            </div>


            {advancedError ? (
              <p className="text-destructive text-xs" role="alert">
                {advancedError}
              </p>
            ) : null}
            {advancedValidationText ? (
              <p className="text-muted-foreground text-xs">{advancedValidationText}</p>
            ) : null}
          </Box>
        </Section>
      ) : null}
        </>
      ) : null}

      <details
        className="rounded-lg border bg-muted/20 p-3 text-xs"
        data-testid="chat-config-advanced-technical"
        id="chat-config-current-settings"
      >
        <summary className="cursor-pointer text-sm font-medium text-foreground">
          {tChat("configAdvancedTechnicalSummary")}
        </summary>
        <div className="mt-3 space-y-3" data-testid="chat-config-current-settings">
          <p className="text-muted-foreground text-xs">{tChat("runtimeEffectiveCollapsedHint")}</p>
          {blockingIssues.length > 0 ? (
            <div
              className="rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground font-mono"
              data-testid="chat-config-advanced-blocking-issues"
            >
              {blockingIssues.map((issue) => (
                <p key={`adv-block-${issue.code}-${issue.field ?? "global"}`}>
                  {formatAdvancedTechnicalValidationIssue(issue)}
                </p>
              ))}
            </div>
          ) : null}
          <div className="space-y-2">
              {advancedTechnicalWarningIssues.length > 0 ? (
                <div
                  className="rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground"
                  role="status"
                  data-testid="chat-config-advanced-validation-warning"
                >
                  {advancedTechnicalWarningIssues.map((issue) => (
                    <p key={`${issue.code}-${issue.field ?? "global"}`}>
                      {formatRuntimeValidationIssueMessage(issue, tChat)}
                    </p>
                  ))}
                </div>
              ) : null}
              <MenuHint>{tChat("chatConfigIndexProfileReindexHint")}</MenuHint>

              {activeSnapQuery.data ? (
                <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                  <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigActiveSnapshotLabel")}</span>
                    <span className="min-w-0 break-all font-mono text-right">{activeSnapQuery.data.id}</span>
                  </div>
                  <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigSnapshotStatusLabel")}</span>
                    <span className="min-w-0 break-all font-mono text-right">{activeSnapQuery.data.status}</span>
                  </div>
                  {activeSnapQuery.data.indexProfileHash ? (
                    <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                      <span className="text-muted-foreground shrink-0">{tChat("chatConfigProfileHashLabel")}</span>
                      <span className="min-w-0 break-all font-mono text-right">{activeSnapQuery.data.indexProfileHash}</span>
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
                  <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigPresetIndexRequirements")}</span>
                    <span className="min-w-0 break-all font-mono text-right">
                      {api.runtimeState.indexCompatibility.presetIndexRequirements.requiredMaterializationStrategy ?? "NONE"}
                    </span>
                  </div>
                  <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigRequiresMetadataSupport")}</span>
                    <span className="min-w-0 break-all font-mono text-right">
                      {String(Boolean(api.runtimeState.indexCompatibility.presetIndexRequirements.requiresMetadataSupport))}
                    </span>
                  </div>
                  {api.runtimeState.indexCompatibility.compatibilityStatus ? (
                    <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                      <span className="text-muted-foreground shrink-0">{tChat("chatConfigCompatibilityLabel")}</span>
                      <span
                        className={cn(
                          "min-w-0 break-all font-mono text-right",
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
                  <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigMaterializationStrategy")}</span>
                    <span className="min-w-0 break-all font-mono text-right">{activeSnapshotCapabilities.materializationStrategy ?? notLoadedLabel}</span>
                  </div>
                  <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatConfigMetadataSupport")}</span>
                    <span className="min-w-0 break-all font-mono text-right">{String(Boolean(activeSnapshotCapabilities.supportsMetadata))}</span>
                  </div>
                  <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <span className="text-muted-foreground shrink-0">{tChat("chatEmbeddingModelLabel")}</span>
                    <span className="min-w-0 break-all font-mono text-right">{activeSnapshotCapabilities.embeddingModelId ?? notLoadedLabel}</span>
                  </div>
                  {activeSnapshotCapabilities.chunkMaxChars != null ? (
                    <div className="mt-1 flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                      <span className="text-muted-foreground shrink-0">{tChat("chatConfigChunkMaxCharsLabel")}</span>
                      <span className="min-w-0 break-all font-mono text-right">{activeSnapshotCapabilities.chunkMaxChars}</span>
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
                    <div key={cap.key} className="flex min-w-0 flex-col gap-0.5 rounded-md border border-dashed bg-muted/20 px-3 py-2">
                      <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
                        <span className="text-muted-foreground min-w-0 break-words">{cap.label ?? cap.key}</span>
                        <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] font-medium uppercase text-muted-foreground">
                          {tChat("chatConfigLockedCapabilityBadge")}
                        </span>
                      </div>
                      <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                        <span className="min-w-0 break-words text-[11px] leading-snug text-muted-foreground">{reason}</span>
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
                <div className="mt-3">
                  <ChatEffectiveRuntimeSummary
                    projectId={api?.projectId}
                    conversationId={api?.conversationId ?? undefined}
                  />
                </div>
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

              <div className="flex flex-wrap gap-2 border-t pt-3">
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
                  {tChat("resetConversationConfigurationToPreset")}
                </button>
              </div>

              <div className="flex flex-wrap gap-2 border-t pt-3">
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
      </details>
    </div>
  );
}

