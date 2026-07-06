"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";
import { useForm, useWatch, type Resolver } from "react-hook-form";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";
import { useMeEffectiveLlmDefaults } from "@/features/settings/hooks/use-me-effective-llm-defaults";
import { usePatchProject, useProject } from "@/features/projects/hooks/use-projects";
import {
  useConfigSchemaQuery,
  useDeleteProjectRagConfig,
  useProjectStoredRagConfigQuery,
  usePutProjectRagConfig,
  usePutUserRagConfig,
  useUserStoredRagConfigQuery,
} from "@/features/settings/hooks/use-rag-config";
import { buildConfigValuesSchema, type ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { labelConfigField } from "@/features/settings/lib/config-field-copy";
import { buildPersonalizationPutPayload } from "@/features/settings/lib/me-canonical-user-config";
import { partitionConfigFields, RETRIEVAL_PARAMETER_FIELD_KEYS, structuredConfigFieldsForMode } from "@/features/settings/lib/rag-config-structured-fields";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";
import {
  buildStoredOverridesPatch,
  clearConfigOverrideKeys,
  EMBEDDING_RESET_TOP_LEVEL_KEYS,
  LLM_RESET_TOP_LEVEL_KEYS,
  mergeEffectiveIntoFormValues,
} from "@/features/settings/lib/effective-config-form-values";
import {
  readAdditionalParameters,
} from "@/features/settings/lib/llm-additional-parameters";
import {
  normalizeLlmProvider,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import { AssistantConfigurationClassifierSection } from "@/features/settings/components/AssistantConfigurationClassifierSection";
import { ProjectIndexProfileSection } from "@/features/settings/components/ProjectIndexProfileSection";
import { AssistantInstructionsEditor } from "@/features/settings/components/AssistantInstructionsEditor";
import { SettingsCollapsibleSection } from "@/features/settings/components/ConfigurationScopeSections";
import { InternalPromptConfigurationSection } from "@/features/settings/components/InternalPromptConfigurationSection";
import { TaskLlmSettingsSection } from "@/features/settings/components/TaskLlmSettingsSection";
import { SettingsRetrievalDefaults } from "@/features/settings/components/SettingsRetrievalDefaults";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { EmbeddingDefaultsSettings } from "@/features/settings/components/EmbeddingDefaultsSettings";
import { ProviderUnsupportedParametersPanel } from "@/features/settings/components/ProviderUnsupportedParametersPanel";
import { RagConfigAdvancedJsonPanel } from "@/features/settings/components/RagConfigAdvancedJsonPanel";
import { RagConfigModelWarnings } from "@/features/settings/components/RagConfigModelWarnings";
import { apiFetch, apiProductPath, ApiError, getSafeApiErrorMessage } from "@/lib/api-client";
import type { MePersonalizationResponse } from "@/types/api";

import { toConfigModelOptions, selectableCatalogModelIds } from "@/lib/product-model-catalog";
import { productProviderLabel, productProviderLabelsFromSettings, ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";

type Mode = "user" | "project";

type RagConfigFormProps = Readonly<{
  mode: Mode;
  projectId?: string;
}>;

function formatProviderLabel(provider: string | undefined, t: (key: string) => string): string | null {
  return productProviderLabel(
    provider,
    productProviderLabelsFromSettings(t as never),
  );
}

function resolveConfigSaveError(error: unknown, genericLabel: string): string {
  if (error && typeof error === "object" && "status" in error && "meta" in error) {
    const parsed = (error as ApiError).meta?.parsedJson;
    if (parsed && typeof parsed === "object" && (parsed as { code?: string }).code === "PROMPT_TEMPLATE_INVALID") {
      return getSafeApiErrorMessage(error);
    }
  }
  return genericLabel;
}

export function RagConfigForm({ mode, projectId }: RagConfigFormProps) {
  const t = useTranslations("Settings");
  const schemaQ = useConfigSchemaQuery();
  const userStoredQ = useUserStoredRagConfigQuery();
  const projectStoredQ = useProjectStoredRagConfigQuery(mode === "project" ? projectId : undefined);
  const selectableModelsQ = useMeSelectableLlmModels("CHAT");
  const selectableEmbeddingQ = useMeSelectableLlmModels("EMBEDDING");
  const llmEffectiveQ = useMeEffectiveLlmDefaults();
  const embeddingEffectiveQ = useMeEffectiveEmbeddingDefaults(null);
  const embeddingEffectiveForForm = mode === "user" ? embeddingEffectiveQ.data : undefined;

  const configData = mode === "user" ? userStoredQ.data : projectStoredQ.data;
  const configLoading = mode === "user" ? userStoredQ.isLoading : projectStoredQ.isLoading;
  const configError = mode === "user" ? userStoredQ.isError : projectStoredQ.isError;

  const schemaFields = schemaQ.data?.fields;
  const fields = useMemo(() => structuredConfigFieldsForMode(schemaFields ?? [], mode), [schemaFields, mode]);
  const isAssistantConfiguration = mode === "user";
  const { instructionFields, behaviorFields } = useMemo(
    () => partitionConfigFields(fields),
    [fields],
  );
  const embeddingModelFields = useMemo(
    () => behaviorFields.filter((f) => f.key === "embeddingModel"),
    [behaviorFields],
  );
  const retrievalParameterFields = useMemo(
    () => behaviorFields.filter((f) => RETRIEVAL_PARAMETER_FIELD_KEYS.has(f.key)),
    [behaviorFields],
  );
  const editableKeys = useMemo(() => fields.map((f) => f.key), [fields]);

  const projectDetailQ = useProject(mode === "project" ? projectId : undefined);
  const patchProject = usePatchProject();

  const [globalPersonaPrompt, setGlobalPersonaPrompt] = useState("");
  const [initialGlobalPersona, setInitialGlobalPersona] = useState("");
  const [personaLoading, setPersonaLoading] = useState(mode === "user");
  const [personaSchemaVersion, setPersonaSchemaVersion] = useState<number | null>(null);
  const [personalizationMap, setPersonalizationMap] = useState<Record<string, unknown>>({});

  const [projectPrompt, setProjectPrompt] = useState("");
  const [initialProjectPrompt, setInitialProjectPrompt] = useState("");

  const [workingConfig, setWorkingConfig] = useState<Record<string, unknown>>({});
  const [additionalParameters, setAdditionalParameters] = useState<Record<string, unknown>>({});

  const effectiveProvider = normalizeLlmProvider(selectableModelsQ.data?.effectiveProvider);

  useEffect(() => {
    if (!configData) return;
    queueMicrotask(() => {
      setWorkingConfig(configData);
      setAdditionalParameters(readAdditionalParameters(configData));
    });
  }, [configData]);

  useEffect(() => {
    if (mode !== "user") return;
    let cancelled = false;
    (async () => {
      setPersonaLoading(true);
      try {
        const res = await apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization"));
        if (cancelled) return;
        const map = { ...res.personalization };
        setPersonalizationMap(map);
        setPersonaSchemaVersion(res.schemaVersion);
        const raw = map.globalPersonaPrompt;
        const value = typeof raw === "string" ? raw : "";
        setGlobalPersonaPrompt(value);
        setInitialGlobalPersona(value);
      } catch {
        /* surfaced via config load patterns */
      } finally {
        if (!cancelled) setPersonaLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [mode]);

  useEffect(() => {
    if (mode !== "project" || !projectDetailQ.data) return;
    const value = projectDetailQ.data.projectPrompt ?? "";
    queueMicrotask(() => {
      setProjectPrompt(value);
      setInitialProjectPrompt(value);
    });
  }, [mode, projectDetailQ.data]);

  const validationSchema = useMemo(() => {
    if (!fields.length) return null;
    return buildConfigValuesSchema(fields);
  }, [fields]);

  const form = useForm<ConfigFormValues>({
    resolver:
      editableKeys.length > 0 && validationSchema
        ? (zodResolver(validationSchema) as Resolver<ConfigFormValues>)
        : undefined,
    defaultValues: {},
  });

  const formSeedKey = useMemo(
    () =>
      JSON.stringify({
        workingConfig,
        editableKeys,
        llmEffective: llmEffectiveQ.data,
        embeddingEffective: embeddingEffectiveForForm,
        effectiveProvider,
      }),
    [workingConfig, editableKeys, llmEffectiveQ.data, embeddingEffectiveForForm, effectiveProvider, mode],
  );
  const lastFormSeedKey = useRef("");

  useEffect(() => {
    if (!workingConfig || !editableKeys.length) return;
    if (lastFormSeedKey.current === formSeedKey) return;
    lastFormSeedKey.current = formSeedKey;
    const merged = mergeEffectiveIntoFormValues(
      workingConfig,
      editableKeys,
      llmEffectiveQ.data,
      embeddingEffectiveForForm,
      effectiveProvider,
      mode,
    );
    form.reset(merged.formValues);
  }, [form, formSeedKey, workingConfig, editableKeys, llmEffectiveQ.data, embeddingEffectiveForForm, effectiveProvider, mode]);

  const putUser = usePutUserRagConfig();
  const putProject = usePutProjectRagConfig(projectId);
  const delProject = useDeleteProjectRagConfig(projectId);

  const saving = mode === "user" ? putUser.isPending : putProject.isPending || patchProject.isPending;
  const clearing = delProject.isPending;

  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [resetLlmDialogOpen, setResetLlmDialogOpen] = useState(false);
  const [resetEmbeddingDialogOpen, setResetEmbeddingDialogOpen] = useState(false);

  const embeddingModelOptions = useMemo(
    () => toConfigModelOptions(selectableEmbeddingQ.data?.models ?? []),
    [selectableEmbeddingQ.data?.models],
  );

  const llmCatalogIds = useMemo(() => selectableCatalogModelIds(selectableModelsQ.data?.models ?? []), [selectableModelsQ.data?.models]);
  const embeddingCatalogIds = useMemo(
    () => selectableCatalogModelIds(selectableEmbeddingQ.data?.models ?? []),
    [selectableEmbeddingQ.data?.models],
  );

  const watchedLlmModel = useWatch({ control: form.control, name: "llmModel" });
  const watchedEmbeddingModel = useWatch({ control: form.control, name: "embeddingModel" });
  const selectedLlmModel = typeof watchedLlmModel === "string" ? String(watchedLlmModel) : "";
  const selectedEmbeddingModel =
    typeof watchedEmbeddingModel === "string" ? String(watchedEmbeddingModel) : "";

  async function onSubmit() {
    const values = form.getValues();
    const payload = buildStoredOverridesPatch({
      mode,
      stored: workingConfig,
      values,
      additionalParameters,
      editableKeys,
      llmEffective: llmEffectiveQ.data,
      embeddingEffective: embeddingEffectiveForForm,
      userStored: mode === "project" ? userStoredQ.data : undefined,
      provider: effectiveProvider,
    });
    if (mode === "user") {
      await putUser.mutateAsync(payload);
      if (globalPersonaPrompt.trim() !== initialGlobalPersona.trim()) {
        const nextMap = buildPersonalizationPutPayload(personalizationMap, {
          theme:
            personalizationMap.theme === "light" ||
            personalizationMap.theme === "dark" ||
            personalizationMap.theme === "system"
              ? personalizationMap.theme
              : "system",
          globalPersonaPrompt,
        });
        const res = await apiFetch<MePersonalizationResponse>(apiProductPath("/me/personalization"), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            schemaVersion: personaSchemaVersion ?? undefined,
            personalization: nextMap,
          }),
        });
        setPersonalizationMap({ ...res.personalization });
        setPersonaSchemaVersion(res.schemaVersion);
        const saved = typeof res.personalization.globalPersonaPrompt === "string"
          ? res.personalization.globalPersonaPrompt
          : "";
        setGlobalPersonaPrompt(saved);
        setInitialGlobalPersona(saved);
      }
    } else if (projectId) {
      await putProject.mutateAsync(payload);
      if (projectPrompt.trim() !== initialProjectPrompt.trim()) {
        await patchProject.mutateAsync({
          id: projectId,
          projectPrompt: projectPrompt.trim() || null,
        });
        setInitialProjectPrompt(projectPrompt.trim());
      }
    }
  }

  function buildClearPatch(keys: readonly string[]): Record<string, unknown> {
    return Object.fromEntries(keys.map((key) => [key, null]));
  }

  async function confirmResetLlmDefaults() {
    const patch = buildClearPatch(LLM_RESET_TOP_LEVEL_KEYS);
    if (mode === "user") {
      await putUser.mutateAsync(patch);
    } else if (projectId) {
      await putProject.mutateAsync(patch);
    }
    const cleared = clearConfigOverrideKeys(workingConfig, LLM_RESET_TOP_LEVEL_KEYS);
    setWorkingConfig(cleared);
    setAdditionalParameters(readAdditionalParameters(cleared));
    setResetLlmDialogOpen(false);
  }

  async function confirmResetEmbeddingDefaults() {
    const patch = buildClearPatch(EMBEDDING_RESET_TOP_LEVEL_KEYS);
    if (mode === "user") {
      await putUser.mutateAsync(patch);
    } else if (projectId) {
      await putProject.mutateAsync(patch);
    }
    const cleared = clearConfigOverrideKeys(workingConfig, EMBEDDING_RESET_TOP_LEVEL_KEYS);
    setWorkingConfig(cleared);
    setResetEmbeddingDialogOpen(false);
  }

  function reloadFormFromConfig(config: Record<string, unknown>) {
    const merged = mergeEffectiveIntoFormValues(
      config,
      editableKeys,
      llmEffectiveQ.data,
      embeddingEffectiveForForm,
      effectiveProvider,
      mode,
    );
    form.reset(merged.formValues);
    setAdditionalParameters(readAdditionalParameters(config));
  }

  async function confirmClearProjectOverrides() {
    await delProject.mutateAsync();
    form.reset({});
    setWorkingConfig({});
    setAdditionalParameters({});
    setClearDialogOpen(false);
  }

  function fieldLabel(fieldKey: string): string {
    if (fieldKey === "provider") return t("configProviderLabel");
    if (fieldKey === "llmModelDefaultOption") return t("configLlmModelDefaultOption");
    if (fieldKey === "embeddingModelDefaultOption") return t("configEmbeddingModelDefaultOption");
    return labelConfigField(fieldKey, (key) => t(key as never));
  }

  function onAdvancedJsonApply(parsed: Record<string, unknown>) {
    setWorkingConfig(parsed);
    reloadFormFromConfig(parsed);
  }

  function onAdditionalParameterChange(key: string, value: number | boolean | undefined) {
    setAdditionalParameters((prev) => {
      const next = { ...prev };
      if (value === undefined) {
        delete next[key];
      } else {
        next[key] = value;
      }
      return next;
    });
  }

  if (mode === "project" && !projectId) {
    return (
      <output className="text-muted-foreground block text-sm">{t("projectConfigNoProject")}</output>
    );
  }

  const schemaError = schemaQ.isError;
  const loadError = schemaError || configError;
  const effectiveProviderLabel = formatProviderLabel(
    selectableModelsQ.data?.effectiveProvider,
    (key) => t(key as never),
  );

  return (
    <Card
      className="@container/rag-config min-w-0 max-w-full overflow-hidden"
      data-testid={mode === "user" ? "user-rag-config-form" : "project-rag-config-form"}
    >
      <CardHeader>
        <CardTitle>{mode === "user" ? t("userConfigTitle") : t("projectConfigTitle")}</CardTitle>
        <CardDescription className="break-words">
          {mode === "user" ? t("userConfigFormDescription") : t("projectConfigFormDescription")}
        </CardDescription>
        {mode === "project" && projectId ? (
          <details className="text-muted-foreground text-xs">
            <summary className="cursor-pointer font-medium">{t("projectConfigAdvancedSummary")}</summary>
            <p className="mt-2 leading-relaxed">{t("projectConfigAdvancedDetails", { projectId })}</p>
          </details>
        ) : null}
      </CardHeader>
      <CardContent className="flex min-w-0 max-w-full flex-col gap-4">
        {(schemaQ.isLoading || configLoading) && (
          <p className="text-muted-foreground text-sm">{t("configLoading")}</p>
        )}
        {loadError && (
          <p className="text-destructive text-sm" role="alert">
            {t("configLoadError")}
          </p>
        )}
        {!schemaQ.isLoading && !configLoading && !loadError && fields.length === 0 && (
          <p className="text-muted-foreground text-sm">{t("configSchemaEmpty")}</p>
        )}
        {!loadError && fields.length > 0 && (
          <>
            <form
              className="flex min-w-0 max-w-full flex-col gap-4"
              data-testid="rag-config-structured-form"
              onSubmit={form.handleSubmit(onSubmit, () => undefined)}
            >
              {isAssistantConfiguration ? (
                <>
                  <SettingsCollapsibleSection
                    title={t("settingsSectionPromptConfiguration")}
                    description={t("assistantProfileSectionDescription")}
                    testId="settings-collapsible-prompt"
                  >
                    <AssistantInstructionsEditor
                      mode={mode}
                      form={form}
                      instructionFields={instructionFields}
                      fieldLabel={fieldLabel}
                      globalPersonaPrompt={globalPersonaPrompt}
                      projectPrompt={projectPrompt}
                      onGlobalPersonaPromptChange={setGlobalPersonaPrompt}
                      onProjectPromptChange={setProjectPrompt}
                      personaLoading={personaLoading}
                    />
                    <InternalPromptConfigurationSection
                      configValues={workingConfig}
                      onChange={setWorkingConfig}
                    />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("taskLlmSettingsTitle")}
                    description={t("taskLlmSettingsDescription")}
                    testId="settings-collapsible-task-models"
                  >
                    <TaskLlmSettingsSection configValues={workingConfig} onChange={setWorkingConfig} />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("settingsSectionEmbeddingModel")}
                    description={t("assistantConfigurationNewProjectDefaultsDescription")}
                    testId="settings-collapsible-embedding"
                  >
                    <p
                      className="text-muted-foreground break-words text-xs"
                      data-testid="settings-config-scope-hint"
                    >
                      {t("settingsScopeAccountDefault")}
                    </p>
                    {embeddingModelFields.length > 0 ? (
                      <>
                        <ConfigSchemaFieldRows
                          fields={embeddingModelFields}
                          form={form}
                          labelFor={fieldLabel}
                          inputIdPrefix="cfg"
                          embeddingModelOptions={embeddingModelOptions}
                          effectiveProviderLabel={effectiveProviderLabel}
                        />
                        <EmbeddingDefaultsSettings form={form} config={workingConfig} />
                      </>
                    ) : null}
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("settingsSectionRetrievalSettings")}
                    testId="settings-collapsible-retrieval"
                  >
                    <SettingsRetrievalDefaults
                      form={form}
                      fields={retrievalParameterFields}
                    />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("assistantConfigurationClassifierTitle")}
                    description={t("assistantConfigurationClassifierDescription")}
                    testId="settings-collapsible-classifier"
                  >
                    <AssistantConfigurationClassifierSection
                      value={
                        typeof workingConfig.classifierModelId === "string" ? workingConfig.classifierModelId : ""
                      }
                      effectiveClassifierModelId={llmEffectiveQ.data?.classifierModelId}
                      onChange={(classifierModelId) => {
                        setWorkingConfig((prev) => {
                          const next = { ...prev };
                          if (classifierModelId.trim()) {
                            next.classifierModelId = classifierModelId.trim();
                          } else {
                            delete next.classifierModelId;
                          }
                          return next;
                        });
                      }}
                    />
                  </SettingsCollapsibleSection>
                </>
              ) : mode === "project" ? (
                <>
                  {projectId ? (
                    <SettingsCollapsibleSection
                      title={t("projectIndexProfileTitle")}
                      description={t("projectIndexProfileDescription")}
                      testId="settings-collapsible-index-profile"
                    >
                      <ProjectIndexProfileSection projectId={projectId} />
                    </SettingsCollapsibleSection>
                  ) : null}

                  <SettingsCollapsibleSection
                    title={t("settingsSectionRetrievalSettings")}
                    description={t("projectRetrievalSettingsDescription")}
                    testId="settings-collapsible-retrieval"
                  >
                    <SettingsRetrievalDefaults
                      form={form}
                      fields={retrievalParameterFields}
                    />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("settingsSectionPromptConfiguration")}
                    description={t("assistantProfileSectionDescription")}
                    testId="settings-collapsible-prompt"
                  >
                    <AssistantInstructionsEditor
                      mode={mode}
                      form={form}
                      instructionFields={instructionFields}
                      fieldLabel={fieldLabel}
                      globalPersonaPrompt={globalPersonaPrompt}
                      projectPrompt={projectPrompt}
                      onGlobalPersonaPromptChange={setGlobalPersonaPrompt}
                      onProjectPromptChange={setProjectPrompt}
                      projectPromptLoading={projectDetailQ.isLoading}
                    />
                    <InternalPromptConfigurationSection
                      configValues={workingConfig}
                      onChange={setWorkingConfig}
                    />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("taskLlmSettingsTitle")}
                    description={t("taskLlmSettingsDescription")}
                    testId="settings-collapsible-task-models"
                  >
                    <TaskLlmSettingsSection configValues={workingConfig} onChange={setWorkingConfig} />
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("settingsSectionEmbeddingModel")}
                    description={t("projectEmbeddingSettingsDescription")}
                    testId="settings-collapsible-embedding"
                  >
                    <p className="text-muted-foreground break-words text-xs" data-testid="project-embedding-index-bound-note">
                      {t("projectEmbeddingSettingsIndexBoundNote")}
                    </p>
                  </SettingsCollapsibleSection>

                  <SettingsCollapsibleSection
                    title={t("assistantConfigurationClassifierTitle")}
                    description={t("assistantConfigurationClassifierDescription")}
                    testId="settings-collapsible-classifier"
                  >
                    <AssistantConfigurationClassifierSection
                      value={
                        typeof workingConfig.classifierModelId === "string" ? workingConfig.classifierModelId : ""
                      }
                      effectiveClassifierModelId={llmEffectiveQ.data?.classifierModelId}
                      onChange={(classifierModelId) => {
                        setWorkingConfig((prev) => {
                          const next = { ...prev };
                          if (classifierModelId.trim()) {
                            next.classifierModelId = classifierModelId.trim();
                          } else {
                            delete next.classifierModelId;
                          }
                          return next;
                        });
                      }}
                    />
                  </SettingsCollapsibleSection>
                </>
              ) : null}

              <RagConfigModelWarnings
                llmModel={selectedLlmModel}
                embeddingModel={selectedEmbeddingModel}
                llmCatalogIds={llmCatalogIds}
                embeddingCatalogIds={embeddingCatalogIds}
              />
              {Object.keys(form.formState.errors).length > 0 && (
                <p className="text-destructive text-sm" role="alert">
                  {t("configValidationError")}
                </p>
              )}
              {(putUser.isError || putProject.isError) && (
                <p className="text-destructive text-sm" role="alert" data-testid="config-save-error">
                  {resolveConfigSaveError(
                    mode === "user" ? putUser.error : putProject.error,
                    t("configSaveError"),
                  )}
                </p>
              )}
              {mode === "project" && delProject.isError && (
                <p className="text-destructive text-sm" role="alert">
                  {t("configDeleteError")}
                </p>
              )}
              <div className="flex flex-wrap gap-2 [&>button]:max-w-full [&>button]:whitespace-normal">
                <Button type="submit" disabled={saving}>
                  {t("configSave")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    if (workingConfig && editableKeys.length) {
                      reloadFormFromConfig(workingConfig);
                    }
                  }}
                >
                  {mode === "project" ? t("projectConfigRevertChanges") : t("configReload")}
                </Button>
                {mode === "user" ? (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setResetLlmDialogOpen(true)}
                      data-testid="reset-llm-defaults-button"
                    >
                      {t("resetLlmDefaultsButton")}
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setResetEmbeddingDialogOpen(true)}
                      data-testid="reset-embedding-defaults-button"
                    >
                      {t("resetEmbeddingDefaultsButton")}
                    </Button>
                  </>
                ) : null}
                {mode === "project" ? (
                  <Button
                    type="button"
                    variant="destructive"
                    disabled={clearing}
                    onClick={() => setClearDialogOpen(true)}
                  >
                    {t("projectConfigClearButton")}
                  </Button>
                ) : null}
              </div>
            </form>
            <details
              className="min-w-0 max-w-full overflow-hidden rounded-md border border-border p-3"
              data-testid="settings-model-parameters-advanced"
            >
              <summary className="cursor-pointer break-words text-sm font-medium">{ADVANCED_TECHNICAL_DETAILS_TITLE}</summary>
              <div className="mt-3 min-w-0 space-y-4">
                <ProviderUnsupportedParametersPanel provider={effectiveProvider} config={workingConfig} />
                <RagConfigAdvancedJsonPanel config={workingConfig} onApply={onAdvancedJsonApply} />
              </div>
            </details>
            {mode === "user" ? (
              <>
                <Dialog open={resetLlmDialogOpen} onOpenChange={setResetLlmDialogOpen}>
                  <DialogContent showCloseButton className="sm:max-w-md">
                    <DialogHeader>
                      <DialogTitle>{t("resetLlmDefaultsDialogTitle")}</DialogTitle>
                      <DialogDescription>{t("resetDefaultsDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <DialogFooter className="gap-2 sm:gap-0">
                      <Button type="button" variant="outline" onClick={() => setResetLlmDialogOpen(false)}>
                        {t("projectConfigClearDialogCancel")}
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        disabled={saving}
                        onClick={() => {
                          confirmResetLlmDefaults().catch(() => {});
                        }}
                      >
                        {t("resetLlmDefaultsDialogConfirm")}
                      </Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
                <Dialog open={resetEmbeddingDialogOpen} onOpenChange={setResetEmbeddingDialogOpen}>
                  <DialogContent showCloseButton className="sm:max-w-md">
                    <DialogHeader>
                      <DialogTitle>{t("resetEmbeddingDefaultsDialogTitle")}</DialogTitle>
                      <DialogDescription>{t("resetDefaultsDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <DialogFooter className="gap-2 sm:gap-0">
                      <Button type="button" variant="outline" onClick={() => setResetEmbeddingDialogOpen(false)}>
                        {t("projectConfigClearDialogCancel")}
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        disabled={saving}
                        onClick={() => {
                          confirmResetEmbeddingDefaults().catch(() => {});
                        }}
                      >
                        {t("resetEmbeddingDefaultsDialogConfirm")}
                      </Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </>
            ) : null}
            {mode === "project" ? (
              <Dialog open={clearDialogOpen} onOpenChange={setClearDialogOpen}>
                <DialogContent showCloseButton className="sm:max-w-md">
                  <DialogHeader>
                    <DialogTitle>{t("projectConfigClearDialogTitle")}</DialogTitle>
                    <DialogDescription>{t("projectConfigClearDialogDescription")}</DialogDescription>
                  </DialogHeader>
                  <DialogFooter className="gap-2 sm:gap-0">
                    <Button type="button" variant="outline" onClick={() => setClearDialogOpen(false)}>
                      {t("projectConfigClearDialogCancel")}
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      disabled={clearing}
                      onClick={() => {
                        confirmClearProjectOverrides().catch(() => {});
                      }}
                    >
                      {t("projectConfigClearDialogConfirm")}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
