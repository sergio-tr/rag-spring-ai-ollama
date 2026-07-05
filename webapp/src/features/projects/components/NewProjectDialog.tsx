"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useProjectCreateFeedbackNotifier } from "@/features/projects/hooks/use-project-create-feedback-notifier";
import { useCreateProject } from "@/features/projects/hooks/use-projects";
import { ProjectCreateError } from "@/features/projects/lib/project-create-errors";
import type { ProjectCreatedDialogOutcome } from "@/features/projects/lib/project-create-feedback";
import { getProjectCreateIndexCombinationFeedback } from "@/features/projects/lib/project-create-index-validation";
import {
  isAdvancedStructuredSearchIndexingEnabled,
  listSelectableProjectMaterializationStrategies,
  type ProjectMaterializationStrategy,
} from "@/features/projects/lib/project-materialization-strategies";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";
import { toConfigModelOptions } from "@/lib/product-model-catalog";
import { Link } from "@/navigation";

const indexProfileSchema = z.object({
  materializationStrategy: z.enum(["CHUNK_LEVEL", "DOCUMENT_LEVEL", "HYBRID", "STRUCTURED_SEARCH"]).optional(),
  metadataEnabled: z.boolean().optional(),
  embeddingModelId: z.string().max(128).optional(),
  chunkMaxChars: z.number().int().min(50).max(5000).optional(),
});

type NewProjectDialogProps = {
  /** Optional extra classes for the dialog trigger button. */
  triggerClassName?: string;
  /**
   * Controlled mode: omit the default trigger and drive open state from the parent
   * (e.g. section actions menu). Both must be set together.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /** Called after a project is created successfully (modal closes). */
  onCreated?: (outcome: ProjectCreatedDialogOutcome) => void;
};

export function NewProjectDialog({
  triggerClassName,
  open: controlledOpen,
  onOpenChange,
  onCreated,
}: Readonly<NewProjectDialogProps>) {
  const t = useTranslations("Projects");
  const schema = useMemo(
    () =>
      z.object({
        name: z.string().min(1, t("projectNameRequired")).max(120, t("projectNameTooLong")),
        description: z.string().max(2000).optional(),
        materializationStrategy: indexProfileSchema.shape.materializationStrategy,
        metadataEnabled: z.boolean().optional(),
        embeddingModelId: z.string().max(128).optional(),
        chunkMaxChars: z.number().int().min(50).max(5000).optional(),
      }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const controlled = controlledOpen !== undefined && onOpenChange !== undefined;
  const open = controlled ? controlledOpen : uncontrolledOpen;
  const setOpenState = controlled ? onOpenChange! : setUncontrolledOpen;
  const { mutateAsync, reset, isPending } = useCreateProject();
  const notifyCreated = useProjectCreateFeedbackNotifier();
  const embeddingCatalogQ = useMeSelectableLlmModels("EMBEDDING");
  const embeddingOptions = useMemo(
    () => toConfigModelOptions(embeddingCatalogQ.data?.models ?? []).filter((o) => !o.disabled),
    [embeddingCatalogQ.data?.models],
  );
  const selectableStrategies = useMemo(() => listSelectableProjectMaterializationStrategies(), []);
  const advancedStructuredSearchEnabled = isAdvancedStructuredSearchIndexingEnabled();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      description: "",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: false,
      embeddingModelId: "",
      chunkMaxChars: 400,
    },
  });

  const selectedStrategy = useWatch({ control: form.control, name: "materializationStrategy" }) ?? "CHUNK_LEVEL";
  const metadataEnabled = useWatch({ control: form.control, name: "metadataEnabled" }) ?? false;
  const indexCombinationFeedback = getProjectCreateIndexCombinationFeedback(
    selectedStrategy,
    metadataEnabled,
  );
  const materializationHelpKey = `materializationStrategyHelp_${selectedStrategy as ProjectMaterializationStrategy}` as
    | "materializationStrategyHelp_DOCUMENT_LEVEL"
    | "materializationStrategyHelp_CHUNK_LEVEL"
    | "materializationStrategyHelp_HYBRID"
    | "materializationStrategyHelp_STRUCTURED_SEARCH";

  function handleOpenChange(next: boolean) {
    reset();
    setSubmitError(null);
    form.reset();
    setOpenState(next);
  }

  async function onSubmit(values: FormValues) {
    if (isPending) {
      return;
    }
    const feedback = getProjectCreateIndexCombinationFeedback(
      values.materializationStrategy ?? "CHUNK_LEVEL",
      values.metadataEnabled ?? false,
    );
    if (feedback.blocked) {
      return;
    }
    setSubmitError(null);
    try {
      const outcome = await mutateAsync({
        name: values.name,
        description: values.description || undefined,
        initialIndexProfile: {
          materializationStrategy: values.materializationStrategy ?? "CHUNK_LEVEL",
          metadataEnabled: values.metadataEnabled ?? false,
          embeddingModelId: values.embeddingModelId?.trim() ? values.embeddingModelId.trim() : null,
          chunkMaxChars: values.chunkMaxChars ?? 400,
          chunkOverlap: null,
          metadataProfile: null,
        },
      });
      (onCreated ?? notifyCreated)(outcome);
      handleOpenChange(false);
    } catch (err) {
      if (err instanceof ProjectCreateError) {
        if (err.kind === "PROJECT_CREATED_RESPONSE_INCOMPLETE") {
          setSubmitError(t("createResponseIncompleteError"));
        } else {
          setSubmitError(t("createError"));
        }
      } else {
        setSubmitError(t("createError"));
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      {!controlled ? (
        <DialogTrigger type="button" className={cn(buttonVariants(), triggerClassName)}>
          {t("newProject")}
        </DialogTrigger>
      ) : null}
      <DialogContent data-testid="new-project-dialog">
        <DialogHeader>
          <DialogTitle>{t("createTitle")}</DialogTitle>
          <DialogDescription>{t("createDescription")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="proj-name">{t("name")}</Label>
            <Input id="proj-name" {...form.register("name")} />
            {form.formState.errors.name && (
              <p className="text-destructive text-sm" role="alert">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="proj-desc">{t("description")}</Label>
            <Input id="proj-desc" {...form.register("description")} />
            <p className="text-muted-foreground text-xs" data-testid="project-create-description-hint">
              {t("descriptionHelper")}
            </p>
          </div>

          <div className="rounded-lg border p-3">
            <p className="text-sm font-medium">{t("indexCapabilitiesSectionTitle")}</p>
            <p className="text-muted-foreground mt-1 text-xs">{t("indexCapabilitiesDisclaimer")}</p>
            <p className="text-muted-foreground mt-2 text-xs">
              {t("projectCreateDefaultsFromAssistantHint")}{" "}
              <Link href="/settings/user" className="text-primary underline-offset-4 hover:underline">
                {t("projectCreateAssistantConfigurationLink")}
              </Link>
              .
            </p>
            <p className="text-muted-foreground mt-1 text-xs" data-testid="project-create-indexing-fixed-hint">
              {t("projectCreateIndexingFixedHint")}
            </p>
            <div className="mt-3 grid grid-cols-1 gap-3">
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-strategy" className="text-xs">
                  {t("materializationStrategyLabel")}
                </Label>
                <select
                  id="proj-strategy"
                  data-testid="project-create-materialization-strategy"
                  className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
                  {...form.register("materializationStrategy")}
                >
                  {selectableStrategies.map((strategy) => (
                    <option key={strategy} value={strategy}>
                      {strategy}
                    </option>
                  ))}
                </select>
                {advancedStructuredSearchEnabled && selectedStrategy === "STRUCTURED_SEARCH" ? (
                  <p
                    className="rounded-md border border-amber-500/40 bg-amber-500/10 px-2 py-1.5 text-xs text-amber-950 dark:text-amber-100"
                    data-testid="project-create-structured-search-advanced-warning"
                    role="status"
                  >
                    {t("structuredSearchAdvancedCreateWarning")}
                  </p>
                ) : null}
                <p
                  className="text-muted-foreground text-xs"
                  data-testid="project-create-materialization-help"
                >
                  {t(materializationHelpKey)}
                </p>
              </div>
              <div className="flex flex-col gap-1">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="border-input size-4 rounded"
                    data-testid="project-create-metadata-capability"
                    {...form.register("metadataEnabled")}
                  />
                  <span>{t("metadataIndexLabel")}</span>
                </label>
                <p className="text-muted-foreground text-xs" data-testid="project-create-metadata-helper">
                  {t("metadataIndexHelper")}
                </p>
              </div>
              {indexCombinationFeedback.blocked && indexCombinationFeedback.blockMessageKey ? (
                <p
                  className="rounded-md border border-destructive/40 bg-destructive/10 px-2 py-1.5 text-xs text-destructive"
                  data-testid="project-create-index-combination-blocked"
                  role="alert"
                >
                  {t(indexCombinationFeedback.blockMessageKey)}
                </p>
              ) : null}
              {!indexCombinationFeedback.blocked && indexCombinationFeedback.warningMessageKey ? (
                <p
                  className="rounded-md border border-amber-500/40 bg-amber-500/10 px-2 py-1.5 text-xs text-amber-950 dark:text-amber-100"
                  data-testid="project-create-index-combination-warning"
                  role="status"
                >
                  {t(indexCombinationFeedback.warningMessageKey)}
                </p>
              ) : null}
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-embed" className="text-xs">
                  {t("embeddingModelLabel")}
                </Label>
                <select
                  id="proj-embed"
                  data-testid="project-create-embedding-model"
                  className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
                  disabled={embeddingCatalogQ.isLoading}
                  {...form.register("embeddingModelId")}
                >
                  <option value="">{t("embeddingModelDefaultOption")}</option>
                  {embeddingOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                {embeddingCatalogQ.isError ? (
                  <p className="text-destructive text-xs" role="alert">
                    {t("embeddingCatalogLoadError")}
                  </p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-chunk" className="text-xs">
                  {t("chunkMaxCharsLabel")}
                </Label>
                <Input id="proj-chunk" type="number" {...form.register("chunkMaxChars", { valueAsNumber: true })} />
              </div>
            </div>
          </div>
          <p className="text-muted-foreground text-xs">
            {t("configurePromptsAfterCreateHint")}{" "}
            <Link href="/settings/project" className="text-primary underline-offset-4 hover:underline">
              {t("configurePromptsAfterCreateAction")}
            </Link>
          </p>
          {submitError ? (
            <p className="text-destructive text-sm" role="alert" data-testid="project-create-error">
              {submitError}
            </p>
          ) : null}
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={isPending || indexCombinationFeedback.blocked}>
              {t("createSubmit")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
