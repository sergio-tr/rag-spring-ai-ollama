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
import {
  useConfigSchemaQuery,
  useDeleteProjectRagConfig,
  useProjectRagConfigQuery,
  usePutProjectRagConfig,
  usePutUserRagConfig,
  useUserRagConfigQuery,
} from "@/features/settings/hooks/use-rag-config";
import { buildConfigValuesSchema, type ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { labelProjectConfigField } from "@/features/settings/lib/project-config-field-copy";
import { mergePayload, pickFormValues } from "@/features/settings/lib/rag-config-values";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";

type Mode = "user" | "project";

type RagConfigFormProps = Readonly<{
  mode: Mode;
  projectId?: string;
}>;

export function RagConfigForm({ mode, projectId }: RagConfigFormProps) {
  const t = useTranslations("Settings");
  const schemaQ = useConfigSchemaQuery();
  const userQ = useUserRagConfigQuery();
  const projectQ = useProjectRagConfigQuery(mode === "project" ? projectId : undefined);

  const configData = mode === "user" ? userQ.data : projectQ.data;
  const configLoading = mode === "user" ? userQ.isLoading : projectQ.isLoading;
  const configError = mode === "user" ? userQ.isError : projectQ.isError;

  const schemaFields = schemaQ.data?.fields;
  const fields = useMemo(() => schemaFields ?? [], [schemaFields]);
  const editableKeys = useMemo(
    () => fields.filter((f) => f.userEditable).map((f) => f.key),
    [fields],
  );

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
    if (!configData || !editableKeys.length) return;
    form.reset(pickFormValues(configData, editableKeys));
  }, [configData, editableKeys, form]);

  const putUser = usePutUserRagConfig();
  const putProject = usePutProjectRagConfig(projectId);
  const delProject = useDeleteProjectRagConfig(projectId);

  const saving = mode === "user" ? putUser.isPending : putProject.isPending;
  const clearing = delProject.isPending;

  const [clearDialogOpen, setClearDialogOpen] = useState(false);

  async function onSubmit(values: ConfigFormValues) {
    const payload = mergePayload(configData, values, editableKeys);
    if (mode === "user") {
      await putUser.mutateAsync(payload);
    } else {
      await putProject.mutateAsync(payload);
    }
  }

  async function confirmClearProjectOverrides() {
    await delProject.mutateAsync();
    form.reset({});
    setClearDialogOpen(false);
  }

  function fieldLabel(fieldKey: string): string {
    if (mode !== "project") return fieldKey;
    return labelProjectConfigField(fieldKey, (key) => t(key as never));
  }

  if (mode === "project" && !projectId) {
    return (
      <output className="text-muted-foreground block text-sm">{t("projectConfigNoProject")}</output>
    );
  }

  const schemaError = schemaQ.isError;
  const loadError = schemaError || configError;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{mode === "user" ? t("userConfigTitle") : t("projectConfigTitle")}</CardTitle>
        <CardDescription>
          {mode === "user"
            ? t("userConfigFormDescription")
            : t("projectConfigFormDescription")}
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
            <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(onSubmit)}>
              <ConfigSchemaFieldRows
                fields={fields}
                form={form}
                labelFor={fieldLabel}
                inputIdPrefix="cfg"
              />
              {Object.keys(form.formState.errors).length > 0 && (
                <p className="text-destructive text-sm" role="alert">
                  {t("configValidationError")}
                </p>
              )}
              {(putUser.isError || putProject.isError) && (
                <p className="text-destructive text-sm" role="alert">
                  {t("configSaveError")}
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
                    if (configData && editableKeys.length) {
                      form.reset(pickFormValues(configData, editableKeys));
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
