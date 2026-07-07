"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { useForm, useWatch, type Resolver } from "react-hook-form";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  formatLatencyTierLabel,
  isDemoBestPresetId,
  resolveExperimentalPresetLatencyTier,
  resolveProductPresetLatencyTier,
} from "@/features/chat/lib/preset-latency-tier";
import { useChatPresetsCatalog } from "@/features/chat/hooks/use-chat-presets-catalog";
import {
  ExperimentalPresetFeatureMatrix,
  ProductPresetFeatureMatrix,
} from "@/features/settings/components/PresetCatalogFeatureBadges";
import { PresetProfileCard } from "@/features/settings/components/PresetProfileCard";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { useConfigSchemaQuery } from "@/features/settings/hooks/use-rag-config";
import { buildConfigValuesSchema, type ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { labelProjectConfigField } from "@/features/settings/lib/project-config-field-copy";
import {
  resolveExperimentalCompatibility,
  resolveProductPresetCompatibility,
} from "@/features/settings/lib/preset-catalog-features";
import {
  buildPresetSaveValues,
  findKeysRejectedBySanitizer,
  partitionPresetImportValues,
} from "@/features/settings/lib/preset-values";
import { isPresetCreationUiEnabled } from "@/features/settings/lib/preset-settings-ui";
import { resolvePresetDisplayName, sortPresetsByRank } from "@/features/presets/lib/preset-display";
import { Link } from "@/navigation";
import {
  productPresetDescription,
  productPresetInternalCodeChip,
  toProductPresetDisplayName,
} from "@/lib/product-preset-labels";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";

const presetsKey = ["presets"] as const;

function PresetCatalogBadge({
  children,
  variant = "muted",
}: Readonly<{ children: React.ReactNode; variant?: "muted" | "primary" | "outline" | "warning" }>) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide",
        variant === "primary" && "bg-primary/15 text-primary",
        variant === "outline" && "border border-border text-muted-foreground",
        variant === "warning" && "border border-amber-500/40 bg-amber-500/10 text-amber-800 dark:text-amber-200",
        variant === "muted" && "bg-muted text-muted-foreground",
      )}
    >
      {children}
    </span>
  );
}

function PresetCompatibilityBadges({
  compatibility,
}: Readonly<{
  compatibility: ReturnType<typeof resolveProductPresetCompatibility>;
}>) {
  const t = useTranslations("Settings");
  if (!compatibility) return null;

  const mat = (compatibility.requiredMaterializationStrategy ?? "").trim();
  return (
    <div className="flex flex-wrap gap-1" data-testid="preset-compatibility-badges">
      {mat ? (
        <PresetCatalogBadge variant="outline">
          {t("presetsCompatMaterialization", { strategy: mat })}
        </PresetCatalogBadge>
      ) : null}
      {compatibility.requiresMetadataSupport ? (
        <PresetCatalogBadge variant="outline">{t("presetsCompatMetadataRequired")}</PresetCatalogBadge>
      ) : null}
    </div>
  );
}

function ProductPresetCatalogRow({ preset }: Readonly<{ preset: RagPresetDto }>) {
  const t = useTranslations("Settings");
  const tChat = useTranslations("Chat");
  const displayName = toProductPresetDisplayName(preset.name);
  const description = productPresetDescription(preset.name, (k) => tChat(k as never)) ?? preset.description;
  const tier = resolveProductPresetLatencyTier(preset);
  const tierLabel = formatLatencyTierLabel(tier, (k) => tChat(k as never));
  const isBaseline = (preset.name ?? "").trim().toLowerCase().includes("worst");
  const recommended = isDemoBestPresetId(preset.id);
  const compatibility = resolveProductPresetCompatibility(preset);

  return (
    <li
      className="bg-muted/40 flex flex-col gap-2 rounded-md border border-border px-3 py-3 text-sm"
      data-testid={`preset-catalog-row-${preset.id}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{displayName}</span>
            <PresetCatalogBadge>{t("presetsSystem")}</PresetCatalogBadge>
            <PresetCatalogBadge variant="outline">{tierLabel}</PresetCatalogBadge>
            {recommended ? (
              <PresetCatalogBadge variant="primary">{t("presetsRecommendedBadge")}</PresetCatalogBadge>
            ) : null}
            {isBaseline ? (
              <PresetCatalogBadge variant="outline">{t("presetsBaselineBadge")}</PresetCatalogBadge>
            ) : null}
          </div>
          {description ? (
            <p className="text-muted-foreground text-xs leading-snug">{description}</p>
          ) : null}
          <PresetCompatibilityBadges compatibility={compatibility} />
          <ProductPresetFeatureMatrix preset={preset} />
        </div>
        <Link
          href="/chat"
          className={cn(buttonVariants({ variant: "outline", size: "sm" }), "shrink-0")}
          data-testid={`preset-use-in-chat-${preset.id}`}
        >
          {t("presetsUseInChat")}
        </Link>
      </div>
    </li>
  );
}

function ExperimentalPresetCatalogRow({ preset }: Readonly<{ preset: ExperimentalPresetCatalogItemDto }>) {
  const t = useTranslations("Settings");
  const tChat = useTranslations("Chat");
  const displayName = resolvePresetDisplayName(preset, (k) => tChat(k as never));
  const description = productPresetDescription(preset.code, (k) => tChat(k as never)) || preset.description;
  const tier = resolveExperimentalPresetLatencyTier(preset.code);
  const tierLabel = formatLatencyTierLabel(tier, (k) => tChat(k as never));
  const codeChip = productPresetInternalCodeChip(preset.code);
  const isBaseline = preset.code === "P0" || preset.code === "P1";
  const compatibility = resolveExperimentalCompatibility(preset);

  return (
    <li
      className="bg-muted/40 flex flex-col gap-2 rounded-md border border-border px-3 py-3 text-sm"
      data-testid={`preset-catalog-row-${preset.code}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{displayName}</span>
            {codeChip ? <PresetCatalogBadge variant="outline">{codeChip}</PresetCatalogBadge> : null}
            <PresetCatalogBadge variant="outline">{tierLabel}</PresetCatalogBadge>
            {preset.labOnly ? (
              <PresetCatalogBadge variant="warning">{t("presetsLabOnlyBadge")}</PresetCatalogBadge>
            ) : null}
            {isBaseline ? (
              <PresetCatalogBadge variant="outline">{t("presetsBaselineBadge")}</PresetCatalogBadge>
            ) : null}
            {preset.code === "P15" ? (
              <PresetCatalogBadge variant="primary">{t("presetsAdvancedBadge")}</PresetCatalogBadge>
            ) : null}
          </div>
          {description ? (
            <p className="text-muted-foreground text-xs leading-snug">{description}</p>
          ) : null}
          <PresetCompatibilityBadges compatibility={compatibility} />
          <ExperimentalPresetFeatureMatrix preset={preset} />
        </div>
        <Link
          href="/chat"
          className={cn(buttonVariants({ variant: "outline", size: "sm" }), "shrink-0")}
          data-testid={`preset-use-in-chat-${preset.code}`}
        >
          {t("presetsUseInChat")}
        </Link>
      </div>
    </li>
  );
}

function sortProductPresets(presets: RagPresetDto[]): RagPresetDto[] {
  const order = ["demo_worst", "demo_naivefullcorpus", "demo_best"];
  return [...presets].sort((a, b) => {
    const ai = order.indexOf((a.name ?? "").trim().toLowerCase());
    const bi = order.indexOf((b.name ?? "").trim().toLowerCase());
    return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
  });
}

function PresetsCreateForm() {
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
              <Button type="button" size="sm" variant="secondary" onClick={() => applyStructuredImport()}>
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
  );
}

export function PresetsSettingsPanel() {
  const t = useTranslations("Settings");
  const creationEnabled = isPresetCreationUiEnabled();

  const catalogQ = useChatPresetsCatalog();

  const productPresets = useMemo(
    () => sortProductPresets(catalogQ.data?.productPresets ?? []),
    [catalogQ.data?.productPresets],
  );
  const experimentalPresets = useMemo(
    () => sortPresetsByRank(catalogQ.data?.experimentalPresets ?? []),
    [catalogQ.data?.experimentalPresets],
  );

  const isEmpty =
    !catalogQ.isLoading && !catalogQ.isError && productPresets.length === 0 && experimentalPresets.length === 0;

  return (
    <div className="flex flex-col gap-6" data-testid="presets-settings-panel">
      <Card>
        <CardHeader>
          <CardTitle>{t("presetsTitle")}</CardTitle>
          <CardDescription>{t("presetsDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p className="text-muted-foreground text-sm" data-testid="presets-catalog-customization-copy">
            {t("presetsCatalogCustomizationHint")}
          </p>

          {catalogQ.isLoading ? <p className="text-muted-foreground text-sm">{t("configLoading")}</p> : null}
          {catalogQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("presetsLoadError")}
            </p>
          ) : null}
          {isEmpty ? (
            <p className="text-muted-foreground text-sm">{t("presetsEmptyCatalog")}</p>
          ) : null}

          {productPresets.length > 0 ? (
            <section className="flex flex-col gap-2" data-testid="presets-catalog-product-section">
              <h3 className="text-sm font-semibold">{t("presetsCatalogProductSectionTitle")}</h3>
              <ul className="flex flex-col gap-2">
                {productPresets.map((p) => (
                  <ProductPresetCatalogRow key={p.id} preset={p} />
                ))}
              </ul>
            </section>
          ) : null}

          {experimentalPresets.length > 0 ? (
            <section className="flex flex-col gap-2" data-testid="presets-catalog-evaluation-section">
              <h3 className="text-sm font-semibold">{t("presetsCatalogEvaluationLadderTitle")}</h3>
              <ul className="flex flex-col gap-2">
                {experimentalPresets.map((p) => (
                  <ExperimentalPresetCatalogRow key={p.code} preset={p} />
                ))}
              </ul>
            </section>
          ) : null}

          {!creationEnabled ? (
            <p className="text-muted-foreground border-t pt-4 text-xs" data-testid="presets-creation-deferred">
              {t("presetsCatalogCreationDeferred")}
            </p>
          ) : null}
        </CardContent>
      </Card>

      {creationEnabled ? <PresetsCreateForm /> : null}
    </div>
  );
}
