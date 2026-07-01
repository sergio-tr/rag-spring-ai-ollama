"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { useForm, useWatch, type Resolver } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PresetProfileCard } from "@/features/settings/components/PresetProfileCard";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { useConfigSchemaQuery } from "@/features/settings/hooks/use-rag-config";
import { buildConfigValuesSchema, type ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { labelProjectConfigField } from "@/features/settings/lib/project-config-field-copy";
import {
  buildPresetSaveValues,
  findKeysRejectedBySanitizer,
  partitionPresetImportValues,
} from "@/features/settings/lib/preset-values";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RagPresetDto } from "@/types/api";

const presetsKey = ["presets"] as const;

export function PresetsSettingsPanel() {
  const t = useTranslations("Settings");
  const qc = useQueryClient();
  const schemaQ = useConfigSchemaQuery();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [extras, setExtras] = useState<Record<string, unknown>>({});
  const [importDraft, setImportDraft] = useState("");
  const [importError, setImportError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [exportCopied, setExportCopied] = useState(false);

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

  const editableKeysSig = useMemo(() => editableKeys.join("|"), [editableKeys]);
  useEffect(() => {
    form.reset({});
  }, [editableKeysSig, form]);

  const watched = useWatch({ control: form.control });
  const previewPayload = useMemo(
    () => buildPresetSaveValues(watched as ConfigFormValues, editableKeys, extras),
    [watched, editableKeys, extras],
  );

  const listQ = useQuery({
    queryKey: presetsKey,
    queryFn: () => apiFetch<RagPresetDto[]>(apiProductPath("/presets")),
  });

  const createM = useMutation({
    mutationFn: async () => {
      const values = buildPresetSaveValues(form.getValues(), editableKeys, extras);
      return apiFetch<RagPresetDto>(apiProductPath("/presets"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          description: description || null,
          tags: [],
          values,
        }),
      });
    },
    onSuccess: () => {
      setName("");
      setDescription("");
      setExtras({});
      setImportDraft("");
      setImportError(null);
      setFormError(null);
      form.reset({});
      qc.invalidateQueries({ queryKey: presetsKey });
    },
    onError: (e: unknown) => {
      setFormError(e instanceof Error ? e.message : t("presetsSaveError"));
    },
  });

  const deleteM = useMutation({
    mutationFn: (id: string) => apiFetch(apiProductPath(`/presets/${id}`), { method: "DELETE" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: presetsKey });
    },
  });

  const items = useMemo(() => listQ.data ?? [], [listQ.data]);

  function schemaFieldLabel(fieldKey: string): string {
    return labelProjectConfigField(fieldKey, (key) => t(key as never));
  }

  function applyStructuredImport() {
    setImportError(null);
    let obj: unknown;
    try {
      obj = JSON.parse(importDraft.trim() || "{}");
    } catch {
      setImportError(t("presetsImportInvalidJson"));
      return;
    }
    if (!obj || typeof obj !== "object" || Array.isArray(obj)) {
      setImportError(t("presetsImportNotObject"));
      return;
    }
    const raw = obj as Record<string, unknown>;
    const rejected = findKeysRejectedBySanitizer(raw);
    if (rejected.length > 0) {
      setImportError(t("presetsImportRejectedKeys", { keys: rejected.join(", ") }));
      return;
    }
    const { structured, extrasAllowed } = partitionPresetImportValues(raw, editableKeys);
    if (validationSchema) {
      const parsed = validationSchema.safeParse(structured);
      if (!parsed.success) {
        setImportError(t("presetsImportValidationFailed"));
        return;
      }
      form.reset(parsed.data as ConfigFormValues);
    } else {
      form.reset(structured);
    }
    setExtras(extrasAllowed);
  }

  async function copyExportPayload() {
    const text = JSON.stringify(
      buildPresetSaveValues(form.getValues(), editableKeys, extras),
      null,
      2,
    );
    await navigator.clipboard.writeText(text);
    setExportCopied(true);
    globalThis.setTimeout(() => setExportCopied(false), 2000);
  }

  const schemaLoadError = schemaQ.isError;
  const schemaLoading = schemaQ.isLoading;

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("presetsTitle")}</CardTitle>
          <CardDescription>{t("presetsDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {listQ.isLoading ? <p className="text-muted-foreground text-sm">{t("configLoading")}</p> : null}
          {listQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("presetsLoadError")}
            </p>
          ) : null}
          {!listQ.isLoading && !listQ.isError && items.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t("presetsEmpty")}</p>
          ) : null}
          <ul className="flex flex-col gap-2">
            {items.map((p) => (
              <li
                key={p.id}
                className="bg-muted/40 flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm"
              >
                <div className="min-w-0 flex-1">
                  <span className="font-medium">{toProductPresetDisplayName(p.name)}</span>
                  {p.system ? (
                    <span className="text-muted-foreground ml-2">({t("presetsSystem")})</span>
                  ) : null}
                  <PresetProfileCard values={p.values ?? {}} presetId={p.id} />
                </div>
                {p.system ? null : (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={deleteM.isPending}
                    onClick={() => deleteM.mutate(p.id)}
                  >
                    {t("presetsDelete")}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Card data-testid="presets-create-card">
        <CardHeader>
          <CardTitle>{t("presetsCreateTitle")}</CardTitle>
          <CardDescription>{t("presetsCreateDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {schemaLoading ? <p className="text-muted-foreground text-sm">{t("configLoading")}</p> : null}
          {schemaLoadError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("configLoadError")}
            </p>
          ) : null}
          {!schemaLoading && !schemaLoadError && editableKeys.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t("presetsStructuredFieldsEmpty")}</p>
          ) : null}

          <div className="grid gap-2">
            <Label htmlFor="preset-name">{t("presetsName")}</Label>
            <Input
              id="preset-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="off"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="preset-desc">{t("presetsDescriptionField")}</Label>
            <Input
              id="preset-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              autoComplete="off"
            />
          </div>

          {!schemaLoading && !schemaLoadError && editableKeys.length > 0 ? (
            <div className="flex flex-col gap-4">
              <ConfigSchemaFieldRows
                fields={fields}
                form={form}
                labelFor={schemaFieldLabel}
                inputIdPrefix="preset-cfg"
              />
              {Object.keys(form.formState.errors).length > 0 ? (
                <p className="text-destructive text-sm" role="alert">
                  {t("configValidationError")}
                </p>
              ) : null}
            </div>
          ) : null}

          <div className="flex flex-col gap-2" data-testid="preset-draft-summary">
            <span className="font-medium text-sm">{t("presetsPreviewTitle")}</span>
            <p className="text-muted-foreground text-xs">{t("presetsPreviewHint")}</p>
            {name.trim() ? (
              <p className="text-sm">
                <span className="text-muted-foreground">{t("presetsName")}: </span>
                <span className="font-medium">{name.trim()}</span>
              </p>
            ) : null}
            {description.trim() ? (
              <p className="text-sm">
                <span className="text-muted-foreground">{t("presetsDescriptionField")}: </span>
                <span>{description.trim()}</span>
              </p>
            ) : null}
            <PresetProfileCard values={previewPayload} presetId="draft" />
          </div>

          <details className="rounded-md border border-border p-3 text-sm">
            <summary className="cursor-pointer font-medium">{t("presetsAdvancedSummary")}</summary>
            <div className="mt-3 flex flex-col gap-3">
              <div className="flex flex-col gap-2">
                <Label htmlFor="preset-import-json">{t("presetsImportLabel")}</Label>
                <Textarea
                  id="preset-import-json"
                  value={importDraft}
                  onChange={(e) => setImportDraft(e.target.value)}
                  className="font-mono text-xs"
                  rows={6}
                  placeholder={t("presetsImportPlaceholder")}
                  data-testid="preset-import-textarea"
                />
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    applyStructuredImport();
                  }}
                >
                  {t("presetsImportApply")}
                </Button>
              </div>
              {importError ? (
                <p className="text-destructive text-sm" role="alert">
                  {importError}
                </p>
              ) : null}
              <div className="flex flex-col gap-2 border-border border-t pt-3">
                <p className="text-muted-foreground text-xs">{t("presetsExportHint")}</p>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    copyExportPayload().catch(() => {});
                  }}
                >
                  {exportCopied ? t("presetsExportCopied") : t("presetsExportCopy")}
                </Button>
              </div>
            </div>
          </details>

          {formError ? (
            <p className="text-destructive text-sm" role="alert">
              {formError}
            </p>
          ) : null}

          <Button
            type="button"
            disabled={createM.isPending || !name.trim() || schemaLoading || schemaLoadError}
            onClick={() => createM.mutate()}
          >
            {t("presetsCreateSubmit")}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
