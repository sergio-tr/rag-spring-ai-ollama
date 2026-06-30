"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
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
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";
import { usePatchProject, useProject } from "@/features/projects/hooks/use-projects";
import {
  useConfigSchemaQuery,
  useDeleteProjectRagConfig,
  useProjectRagConfigQuery,
  usePutProjectRagConfig,
  usePutUserRagConfig,
  useUserRagConfigQuery,
} from "@/features/settings/hooks/use-rag-config";
import { buildConfigValuesSchema, type ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { labelConfigField } from "@/features/settings/lib/config-field-copy";
import { buildPersonalizationPutPayload } from "@/features/settings/lib/me-canonical-user-config";
import { partitionConfigFields, structuredConfigFields } from "@/features/settings/lib/rag-config-structured-fields";
import { mergePayload, pickFormValues } from "@/features/settings/lib/rag-config-values";
import {
  mergeAdditionalParametersIntoPayload,
  readAdditionalParameters,
  readTemperature,
} from "@/features/settings/lib/llm-additional-parameters";
import {
  LLM_TEMPERATURE_KEY,
  normalizeLlmProvider,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";
import { AssistantInstructionsEditor } from "@/features/settings/components/AssistantInstructionsEditor";
import { InternalPromptConfigurationSection } from "@/features/settings/components/InternalPromptConfigurationSection";
import { TaskLlmSettingsSection } from "@/features/settings/components/TaskLlmSettingsSection";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { EffectiveModelParametersPreview } from "@/features/settings/components/EffectiveModelParametersPreview";
import { ProviderAwareModelParameters } from "@/features/settings/components/ProviderAwareModelParameters";
import { ProviderUnsupportedParametersPanel } from "@/features/settings/components/ProviderUnsupportedParametersPanel";
import { RagConfigAdvancedJsonPanel } from "@/features/settings/components/RagConfigAdvancedJsonPanel";
import { RagConfigModelWarnings } from "@/features/settings/components/RagConfigModelWarnings";
import { UserAccountPreferencesSection } from "@/features/settings/components/UserAccountPreferencesSection";
import { apiFetch, apiProductPath, ApiError, getSafeApiErrorMessage } from "@/lib/api-client";
import type { MePersonalizationResponse } from "@/types/api";

import { toConfigModelOptions, selectableCatalogModelIds } from "@/lib/product-model-catalog";
import { productProviderLabel, productProviderLabelsFromSettings } from "@/lib/product-provider-labels";

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
  const userQ = useUserRagConfigQuery();
  const projectQ = useProjectRagConfigQuery(mode === "project" ? projectId : undefined);
  const selectableModelsQ = useMeSelectableLlmModels("CHAT");
  const selectableEmbeddingQ = useMeSelectableLlmModels("EMBEDDING");

  const configData = mode === "user" ? userQ.data : projectQ.data;
  const configLoading = mode === "user" ? userQ.isLoading : projectQ.isLoading;
  const configError = mode === "user" ? userQ.isError : projectQ.isError;

  const schemaFields = schemaQ.data?.fields;
  const fields = useMemo(() => structuredConfigFields(schemaFields ?? []), [schemaFields]);
  const { instructionFields, behaviorFields } = useMemo(
    () => partitionConfigFields(fields),
    [fields],
  );
  const modelConfigurationFields = useMemo(
    () => behaviorFields.filter((f) => f.key === "llmModel"),
    [behaviorFields],
  );
  const embeddingModelFields = useMemo(
    () => behaviorFields.filter((f) => f.key === "embeddingModel"),
    [behaviorFields],
  );
  const retrievalSettingsFields = useMemo(
    () =>
      behaviorFields.filter(
        (f) =>
          f.key !== "llmModel" &&
          f.key !== LLM_TEMPERATURE_KEY &&
          f.key !== "temperature" &&
          f.key !== "embeddingModel",
      ),
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
    if (configData) {
      setWorkingConfig(configData);
      setAdditionalParameters(readAdditionalParameters(configData));
    }
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
    setProjectPrompt(value);
    setInitialProjectPrompt(value);
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

  useEffect(() => {
    if (!workingConfig || !editableKeys.length) return;
    const picked = pickFormValues(workingConfig, editableKeys);
    const temp = readTemperature(workingConfig);
    if (temp !== undefined) {
      picked[LLM_TEMPERATURE_KEY] = temp;
    }
    form.reset(picked);
  }, [workingConfig, editableKeys, form]);

  const putUser = usePutUserRagConfig();
  const putProject = usePutProjectRagConfig(projectId);
  const delProject = useDeleteProjectRagConfig(projectId);

  const saving = mode === "user" ? putUser.isPending : putProject.isPending || patchProject.isPending;
  const clearing = delProject.isPending;

  const [clearDialogOpen, setClearDialogOpen] = useState(false);

  const llmModelOptions = useMemo(
    () => toConfigModelOptions(selectableModelsQ.data?.models ?? []),
    [selectableModelsQ.data?.models],
  );

  const embeddingModelOptions = useMemo(
    () => toConfigModelOptions(selectableEmbeddingQ.data?.models ?? []),
    [selectableEmbeddingQ.data?.models],
  );

  const llmCatalogIds = useMemo(() => selectableCatalogModelIds(selectableModelsQ.data?.models ?? []), [selectableModelsQ.data?.models]);
  const embeddingCatalogIds = useMemo(
    () => selectableCatalogModelIds(selectableEmbeddingQ.data?.models ?? []),
    [selectableEmbeddingQ.data?.models],
  );

  const selectedLlmModel = typeof form.watch("llmModel") === "string" ? String(form.watch("llmModel")) : "";
  const selectedEmbeddingModel =
    typeof form.watch("embeddingModel") === "string" ? String(form.watch("embeddingModel")) : "";

  async function onSubmit(values: ConfigFormValues) {
    let payload = mergePayload(workingConfig, values, editableKeys);
    if (values[LLM_TEMPERATURE_KEY] === undefined) {
      delete payload[LLM_TEMPERATURE_KEY];
      delete payload.temperature;
    }
    payload = mergeAdditionalParametersIntoPayload(payload, additionalParameters);
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
    setAdditionalParameters(readAdditionalParameters(parsed));
    const picked = pickFormValues(parsed, editableKeys);
    const temp = readTemperature(parsed);
    if (temp !== undefined) {
      picked[LLM_TEMPERATURE_KEY] = temp;
    }
    form.reset(picked);
  }

  function onAdditionalParameterChange(key: string, value: number | undefined) {
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

  const watchedTemperature = form.watch(LLM_TEMPERATURE_KEY as keyof ConfigFormValues);

  const previewConfig = useMemo(() => {
    const merged: Record<string, unknown> = {
      ...workingConfig,
      llmAdditionalParameters: additionalParameters,
    };
    if (typeof watchedTemperature === "number") {
      merged[LLM_TEMPERATURE_KEY] = watchedTemperature;
    }
    return merged;
  }, [workingConfig, additionalParameters, watchedTemperature]);

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
    <Card data-testid={mode === "user" ? "user-rag-config-form" : "project-rag-config-form"}>
      <CardHeader>
        <CardTitle>{mode === "user" ? t("userConfigTitle") : t("projectConfigTitle")}</CardTitle>
        <CardDescription>
          {mode === "user" ? t("userConfigFormDescription") : t("projectConfigFormDescription")}
        </CardDescription>
        {mode === "project" && projectId ? (
          <details className="text-muted-foreground text-xs">
            <summary className="cursor-pointer font-medium">{t("projectConfigAdvancedSummary")}</summary>
            <p className="mt-2 leading-relaxed">{t("projectConfigAdvancedDetails", { projectId })}</p>
          </details>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
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
              className="flex flex-col gap-6"
              data-testid="rag-config-structured-form"
              onSubmit={form.handleSubmit(onSubmit)}
            >
              <section className="flex flex-col gap-4" data-testid="assistant-profile-section">
                <div>
                  <h3 className="text-sm font-medium">{t("assistantProfileSectionTitle")}</h3>
                  <p className="text-muted-foreground mt-1 text-xs">{t("assistantProfileSectionDescription")}</p>
                </div>
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
                  projectPromptLoading={mode === "project" && projectDetailQ.isLoading}
                />
                <InternalPromptConfigurationSection
                  configValues={workingConfig}
                  onChange={setWorkingConfig}
                />
              </section>

              <section className="flex flex-col gap-4 border-t pt-4" data-testid="task-llm-settings-section">
                <details className="rounded-md border bg-muted/20 p-3 text-sm">
                  <summary className="cursor-pointer font-medium">{t("taskLlmSettingsSummary")}</summary>
                  <div className="mt-3">
                    <TaskLlmSettingsSection configValues={workingConfig} onChange={setWorkingConfig} />
                  </div>
                </details>
              </section>

              <section className="flex flex-col gap-4 border-t pt-4" data-testid="assistant-behavior-section">
                <div>
                  <h3 className="text-sm font-medium">{t("assistantBehaviorSectionTitle")}</h3>
                  <p className="text-muted-foreground mt-1 text-xs">{t("assistantBehaviorSectionDescription")}</p>
                  <p className="text-muted-foreground mt-2 text-xs" data-testid="settings-config-scope-hint">
                    {mode === "user" ? t("settingsScopeAccountDefault") : t("settingsScopeProjectSetting")}
                  </p>
                </div>

                {modelConfigurationFields.length > 0 ? (
                  <div className="space-y-3" data-testid="settings-model-configuration-section">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      {t("settingsSectionModelConfiguration")}
                    </h4>
                    <ConfigSchemaFieldRows
                      fields={modelConfigurationFields}
                      form={form}
                      labelFor={fieldLabel}
                      inputIdPrefix="cfg"
                      llmModelOptions={llmModelOptions}
                      effectiveProviderLabel={effectiveProviderLabel}
                    />
                    <ProviderAwareModelParameters
                      provider={effectiveProvider}
                      form={form}
                      additionalParameters={additionalParameters}
                      onAdditionalParameterChange={onAdditionalParameterChange}
                    />
                    <EffectiveModelParametersPreview provider={effectiveProvider} config={previewConfig} />
                  </div>
                ) : null}

                {embeddingModelFields.length > 0 ? (
                  <div className="space-y-3" data-testid="settings-embedding-model-section">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      {t("settingsSectionEmbeddingModel")}
                    </h4>
                    <ConfigSchemaFieldRows
                      fields={embeddingModelFields}
                      form={form}
                      labelFor={fieldLabel}
                      inputIdPrefix="cfg"
                      embeddingModelOptions={embeddingModelOptions}
                      effectiveProviderLabel={effectiveProviderLabel}
                    />
                  </div>
                ) : null}

                {retrievalSettingsFields.length > 0 ? (
                  <div className="space-y-3" data-testid="settings-retrieval-settings-section">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      {t("settingsSectionRetrievalSettings")}
                    </h4>
                    <ConfigSchemaFieldRows
                      fields={retrievalSettingsFields}
                      form={form}
                      labelFor={fieldLabel}
                      inputIdPrefix="cfg"
                    />
                  </div>
                ) : null}
              </section>
              <RagConfigModelWarnings
                llmModel={selectedLlmModel}
                embeddingModel={selectedEmbeddingModel}
                llmCatalogIds={llmCatalogIds}
                embeddingCatalogIds={embeddingCatalogIds}
              />
              {mode === "user" ? <UserAccountPreferencesSection /> : null}
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
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={saving}>
                  {t("configSave")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    if (workingConfig && editableKeys.length) {
                      const picked = pickFormValues(workingConfig, editableKeys);
                      const temp = readTemperature(workingConfig);
                      if (temp !== undefined) {
                        picked[LLM_TEMPERATURE_KEY] = temp;
                      }
                      form.reset(picked);
                      setAdditionalParameters(readAdditionalParameters(workingConfig));
                    }
                  }}
                >
                  {mode === "project" ? t("projectConfigRevertChanges") : t("configReload")}
                </Button>
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
              className="rounded-md border border-border p-3"
              data-testid="settings-model-parameters-advanced"
            >
              <summary className="cursor-pointer text-sm font-medium">{ADVANCED_TECHNICAL_DETAILS_TITLE}</summary>
              <div className="mt-3 space-y-4">
                <ProviderUnsupportedParametersPanel provider={effectiveProvider} config={workingConfig} />
                <RagConfigAdvancedJsonPanel config={workingConfig} onApply={onAdvancedJsonApply} />
              </div>
            </details>
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
