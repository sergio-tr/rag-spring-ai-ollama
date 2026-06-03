"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { useClassifierModelsQuery } from "@/features/lab/hooks/use-classifier-registry";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { useActiveProjectSnapshot } from "@/features/projects/hooks/use-active-project-snapshot";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import {
  ChatConfigTechnicalDetails,
  CompactSummaryRow,
} from "@/features/chat/components/chat-config-compact-ui";
import { chatFailureHintForCode, normalizeChatFailureCode } from "@/features/chat/lib/chat-job-errors";
import { cn } from "@/lib/utils";
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
  return <div className="rounded-lg border bg-background/60 p-3">{children}</div>;
}

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block max-w-[22rem] text-xs font-normal leading-snug">
      {children}
    </output>
  );
}

function DisabledReason({
  reason,
  id,
}: Readonly<{
  reason: string;
  id: string;
}>) {
  return (
    <Popover>
      <PopoverTrigger
        aria-describedby={id}
        className="text-muted-foreground hover:text-foreground inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium"
        type="button"
      >
        Disabled
      </PopoverTrigger>
      <PopoverContent id={id} className="w-80 text-xs">
        <p className="text-muted-foreground text-xs leading-relaxed">{reason}</p>
      </PopoverContent>
    </Popover>
  );
}

function experimentalPresetOptionLabel(p: {
  code: string;
  label: string;
  supported: boolean;
  supportStatus: string;
  reasonIfUnsupported: string | null;
  requiresMultiTurn: boolean;
  chatSelectable: boolean;
}) {
  const base = `${p.code} — ${p.label}`;
  if (p.requiresMultiTurn) return `${base} [REQUIRES_MULTI_TURN]`;
  if (!p.chatSelectable)
    return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
  if (p.supported) return base;
  return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
}

type EffectiveConfigMap = Record<string, unknown>;
const NOT_LOADED_LABEL = "Not loaded";

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
 * 3) Model & preset
 * 4) Runtime configuration
 */
export function ChatConfigurationPanelContent() {
  const t = useTranslations("SectionActions");
  const tChat = useTranslations("Chat");
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
  const activeClassifierModel =
    api?.classifierModelChoice?.trim() ||
    api?.runtimeState?.conversationClassifierModelId?.trim() ||
    "";
  const indexProfileQuery = useProjectIndexProfile(api?.projectId);
  const activeSnapQuery = useActiveProjectSnapshot(api?.projectId);

  const effectiveLoading = Boolean(api?.runtimeStateLoading);
  const effectiveError = api?.runtimeStateError ?? null;
  const patchPending = Boolean(api?.patchConvPending);

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

  const indexBoundCaps = useMemo(() => {
    const filtered = caps.filter((c) => c.category === "INDEX_BOUND" && c.visibleInChat === true);
    filtered.sort((a, b) => a.displayOrder - b.displayOrder);
    return filtered;
  }, [caps]);

  const mergedRuntimeFlagValues = useMemo(() => {
    // Effective config already includes runtimeOverride; keep this name to avoid invasive refactors in R1.
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
    if (issue?.message) return issue.message;
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
      ? api.runtimeState.preset.supportStatus || "NOT_SUPPORTED"
      : null;
  const selectedPresetDisabledReason =
    api?.runtimeState?.disabledPresetReason ??
    api?.runtimeState?.presetCompatibility?.disabledReason ??
    issueByField.get("preset")?.message ??
    issueByField.get("presetId")?.message ??
    null;

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
      return p.reasonIfUnsupported || p.supportStatus || "This preset is not selectable in Chat.";
    }
    const req = p.indexRequirements;
    if (!req) return null;
    const idx = api?.runtimeState?.indexCompatibility;
    const caps = idx?.activeSnapshotCapabilities;
    if (!idx?.hasActiveIndex || !caps) {
      return "Create or reindex the project with a compatible index profile.";
    }
    const activeMat = caps.materializationStrategy;
    const requiredMat = req.requiredMaterializationStrategy;
    const matOk =
      !requiredMat ||
      activeMat === requiredMat ||
      (activeMat === "HYBRID" && requiredMat === "CHUNK_LEVEL");
    if (!matOk) {
      return "Create or reindex the project with a compatible index profile.";
    }
    if (req.requiresMetadataSupport && caps.supportsMetadata !== true) {
      return "Create or reindex the project with metadata support enabled.";
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

  const manualOverrideKeySet = useMemo(
    () => new Set(api?.runtimeState?.manualOverrideKeys ?? []),
    [api?.runtimeState?.manualOverrideKeys],
  );

  function formatIndexBoundCapabilityValue(key: string): string {
    const profile = indexProfileQuery.data;
    const snapCaps = api?.runtimeState?.indexCompatibility?.activeSnapshotCapabilities;
    const ec = effectiveConfig as EffectiveConfigMap | null;
    switch (key) {
      case "materializationStrategy": {
        if (profile?.materializationStrategy) return profile.materializationStrategy;
        const v = ec?.materializationStrategy;
        return v != null && v !== "" ? String(v) : NOT_LOADED_LABEL;
      }
      case "metadataEnabled": {
        if (profile) return String(Boolean(profile.metadataEnabled));
        const v = ec?.metadataEnabled;
        return typeof v === "boolean" ? String(v) : NOT_LOADED_LABEL;
      }
      case "embeddingModel": {
        const fromProfile = profile?.embeddingModelId;
        if (fromProfile) return fromProfile;
        const fromSnap = snapCaps?.embeddingModelId;
        if (fromSnap) return fromSnap;
        const v = ec?.embeddingModel;
        return v != null && String(v).trim() !== "" ? String(v) : NOT_LOADED_LABEL;
      }
      case "chunkMaxChars": {
        if (profile != null) return String(profile.chunkMaxChars);
        if (snapCaps?.chunkMaxChars != null) return String(snapCaps.chunkMaxChars);
        return NOT_LOADED_LABEL;
      }
      case "chunkOverlap": {
        if (profile?.chunkOverlap != null) return String(profile.chunkOverlap);
        if (snapCaps?.chunkOverlap != null) return String(snapCaps.chunkOverlap);
        return NOT_LOADED_LABEL;
      }
      default:
        return NOT_LOADED_LABEL;
    }
  }

  // Ensure the draft starts from persisted overrides, not from the preset.
  // (We still display effective config separately via validate.)
  const productIds = useMemo(() => new Set((api?.presets ?? []).map((p) => p.id)), [api?.presets]);
  const experimentalUnique = useMemo(
    () => (api?.experimentalPresets ?? []).filter((p) => !productIds.has(p.productPresetId)),
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
  const displayPresetLabel =
    api?.runtimeState?.preset?.label?.trim() ||
    api?.presets?.find((p) => p.id === runtimeSelectedPresetId)?.name ||
    tChat("presetRecommendedDefault");
  const documentsReadyLabel =
    documentCounts.ready > 0
      ? tChat("configCompactDocumentsReady", { ready: documentCounts.ready })
      : tChat("configCompactDocumentsNone");
  const indexStatusLabel = useMemo(() => {
    if (api?.runtimeState?.requiresReindex) return tChat("configCompactIndexIncompatible");
    const status = api?.runtimeState?.indexCompatibility?.compatibilityStatus;
    if (status === "INCOMPATIBLE") return tChat("configCompactIndexIncompatible");
    if (activeSnapQuery.data?.id) return tChat("configCompactIndexCompatible");
    return tChat("configCompactIndexUnknown");
  }, [
    activeSnapQuery.data?.id,
    api?.runtimeState?.indexCompatibility?.compatibilityStatus,
    api?.runtimeState?.requiresReindex,
    tChat,
  ]);

  return (
    <div className="flex flex-col gap-6">
      {blockingIssues.length > 0 ? (
        <div data-testid="chat-error">
          <div
            className="rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm"
            data-testid="chat-runtime-blocking-banner"
            role="alert"
          >
            <p className="font-medium text-destructive">Configuration is invalid.</p>
            <ul className="mt-1 space-y-1 text-xs text-muted-foreground">
              {blockingIssues.map((issue) => (
                <li key={`${issue.code}-${issue.field ?? "global"}`}>
                  <span className="font-mono text-destructive" data-testid={`chat-error-code-${actionableIssueCode(issue) ?? "UNKNOWN"}`}>
                    {actionableIssueCode(issue) ?? "UNKNOWN"}
                  </span>
                  <span> — {chatFailureHintForCode(issue.code, tChat) ?? issue.message}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}

      {warningIssues.length > 0 ? (
        <div className="rounded-lg border bg-muted/30 px-3 py-2 text-xs text-muted-foreground" role="status">
          {warningIssues[0]?.message}
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
            value={<span data-testid="chat-config-summary-model">{displayModelLabel}</span>}
          />
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
      <Section title="Document scope">
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
                <span className="text-muted-foreground block">Documents</span>
                <span className="font-mono">{documentCounts.total}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">READY</span>
                <span className="font-mono">{documentCounts.ready}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">INGESTING</span>
                <span className="font-mono">{documentCounts.ingesting}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">ERROR</span>
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

      <Section title="Model & preset">
        <Box>
          <div className="space-y-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor={modelSelectId} className="text-xs">
                {tChat("modelLabel")}
              </Label>
              <select
                id={modelSelectId}
                data-testid="chat-llm-model-select"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  api?.modelsError && "border-destructive",
                )}
                value={api?.llmModelChoice ?? ""}
                onChange={(e) => api?.setLlmModelChoice(e.target.value)}
                disabled={needsProject || needsConversation || !!api?.modelsError || patchPending}
                aria-label={tChat("modelLabel")}
              >
                <option value="">{tChat("modelDefault")}</option>
                {api?.modelsCatalog?.allowlist
                  ?.filter((e) => e.type === "LLM")
                  .sort((a, b) => a.name.localeCompare(b.name))
                  .map((m) => {
                    const usable = m.inAllowlist && m.installedInOllama;
                    return (
                      <option key={m.name} value={m.name} disabled={!usable}>
                        {m.name}
                        {!m.installedInOllama ? ` (${tChat("modelNotInstalled")})` : ""}
                        {!m.inAllowlist ? ` (${tChat("modelNotAllowlisted")})` : ""}
                      </option>
                    );
                  })}
              </select>
              {api?.modelsError ? (
                <output className="text-destructive text-xs" data-testid="chat-error-code-MODEL_UNAVAILABLE">
                  {api.modelsErrorMessage || tChat("chatJobFailure_MODEL_UNAVAILABLE")}
                </output>
              ) : null}
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor={classifierSelectId} className="text-xs">
                {tChat("classifierLabel")}
              </Label>
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
                    <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                      {selectedExperimental.code}
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
                <optgroup label="Product presets">
                  {api?.presets?.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Experimental presets (P0–P14)">
                  {experimentalUnique.map((p) => {
                    const reason = presetIndexDisabledReason(p);
                    return (
                      <option
                        key={p.productPresetId}
                        value={p.productPresetId}
                        disabled={Boolean(reason)}
                        title={reason ?? undefined}
                      >
                        {reason && p.chatSelectable
                          ? `${experimentalPresetOptionLabel(p)} [${reason}]`
                          : experimentalPresetOptionLabel(p)}
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
        </>
      ) : null}

      <ChatConfigTechnicalDetails summary={tChat("configTechnicalDetails")} testId="chat-config-technical-details">
          <div className="space-y-2">
            <MenuHint>
              Index profile values require reindexing to change.
            </MenuHint>

            {activeSnapQuery.data ? (
              <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Active snapshot</span>
                  <span className="font-mono">{activeSnapQuery.data.id}</span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Status</span>
                  <span className="font-mono">{activeSnapQuery.data.status}</span>
                </div>
                {activeSnapQuery.data.indexProfileHash ? (
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">Profile hash</span>
                    <span className="font-mono">{activeSnapQuery.data.indexProfileHash}</span>
                  </div>
                ) : null}
              </div>
            ) : (
              <div data-testid="chat-snapshot-warning">
                <MenuHint>No active index snapshot yet.</MenuHint>
              </div>
            )}

            {api?.runtimeState?.indexCompatibility?.presetIndexRequirements ? (
              <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Preset index requirements</span>
                  <span className="font-mono">
                    {api.runtimeState.indexCompatibility.presetIndexRequirements.requiredMaterializationStrategy ?? "NONE"}
                  </span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Requires metadata support</span>
                  <span className="font-mono">
                    {String(Boolean(api.runtimeState.indexCompatibility.presetIndexRequirements.requiresMetadataSupport))}
                  </span>
                </div>
                {api.runtimeState.indexCompatibility.compatibilityStatus ? (
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">Compatibility</span>
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
                  <span className="text-muted-foreground">Materialization strategy</span>
                  <span className="font-mono">{activeSnapshotCapabilities.materializationStrategy ?? NOT_LOADED_LABEL}</span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Metadata support</span>
                  <span className="font-mono">{String(Boolean(activeSnapshotCapabilities.supportsMetadata))}</span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Embedding model</span>
                  <span className="font-mono">{activeSnapshotCapabilities.embeddingModelId ?? NOT_LOADED_LABEL}</span>
                </div>
                {activeSnapshotCapabilities.chunkMaxChars != null ? (
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">Chunk max chars</span>
                    <span className="font-mono">{activeSnapshotCapabilities.chunkMaxChars}</span>
                  </div>
                ) : null}
              </div>
            ) : null}

            {api?.runtimeState?.requiresReindex ? (
              <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-xs" data-testid="chat-snapshot-warning">
                <p className="text-destructive font-medium">Reindex required for this preset.</p>
                <p className="text-muted-foreground mt-1">
                  The active snapshot does not satisfy the preset’s index requirements (materialization/metadata). Reindex the
                  project or pick a compatible preset.
                </p>
              </div>
            ) : null}

            <div className="grid grid-cols-1 gap-2 text-sm">
              {indexBoundCaps.map((cap) => {
                const reason = cap.reasonIfDisabled ?? "Changing this requires a new index snapshot (reindex) or a compatible project profile.";
                return (
                  <div key={cap.key} className="flex flex-col gap-0.5 rounded-md border border-dashed bg-muted/20 px-3 py-2">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-muted-foreground">{cap.label ?? cap.key}</span>
                      <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] font-medium uppercase text-muted-foreground">
                        Locked
                      </span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-[11px] leading-snug text-muted-foreground">{reason}</span>
                      <span className="shrink-0 font-mono text-xs">{formatIndexBoundCapabilityValue(cap.key)}</span>
                    </div>
                  </div>
                );
              })}
            </div>

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
              {effectiveConfig ? (
                <div className="mt-2 grid max-h-64 grid-cols-1 gap-1 overflow-y-auto text-xs" data-testid="chat-config-effective-keys">
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
              ) : (
                <output className="text-muted-foreground mt-2 block text-xs">Not loaded.</output>
              )}
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
      </ChatConfigTechnicalDetails>

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
                  <span className="text-muted-foreground text-xs">{runtimeOpen ? "Hide" : "Show"}</span>
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
                {effectiveLoading ? "Loading..." : "Refresh"}
              </button>
            </div>

            {runtimeOpen ? (
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label className="flex flex-col gap-1 text-sm">
                    <span>topK</span>
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
                    <span>similarityThreshold</span>
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
                  {runtimeToggles.map((cap) => {
                    const key = cap.key;
                    const reason = disabledReason(key);
                    const fieldIssue = issueByField.get(key);
                    const rid = `disabled-${key}`;
                    const showMultiTurn = cap.supportMode === "MULTI_TURN_REQUIRED";
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
                            <span>{cap.label ?? key}</span>
                          </label>
                          <div className="flex items-center gap-2">
                            {showMultiTurn ? (
                              <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                                Multi-turn
                              </span>
                            ) : null}
                            {reason ? <DisabledReason id={rid} reason={reason} /> : null}
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

