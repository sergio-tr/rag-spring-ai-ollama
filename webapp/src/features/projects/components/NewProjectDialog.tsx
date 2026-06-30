"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
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
import { useCreateProject } from "@/features/projects/hooks/use-projects";

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
};

export function NewProjectDialog({
  triggerClassName,
  open: controlledOpen,
  onOpenChange,
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
  const { mutateAsync, reset, isPending, isError } = useCreateProject();
  const [createWarning, setCreateWarning] = useState<string | null>(null);

  function handleOpenChange(next: boolean) {
    if (next) {
      reset();
      setCreateWarning(null);
    }
    setOpenState(next);
  }

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

  async function onSubmit(values: FormValues) {
    setCreateWarning(null);
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
      if (outcome.activateFailed) {
        setCreateWarning(t("createActivateWarning"));
      } else if (outcome.reconciledFromList) {
        setCreateWarning(t("createReconciledWarning"));
      }
      handleOpenChange(false);
      form.reset();
    } catch {
      // Critical failure only when POST did not yield a project (see useCreateProject).
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      {!controlled ? (
        <DialogTrigger type="button" className={cn(buttonVariants(), triggerClassName)}>
          {t("newProject")}
        </DialogTrigger>
      ) : null}
      <DialogContent>
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
          </div>

          <div className="rounded-lg border p-3">
            <p className="text-sm font-medium">{t("indexCapabilitiesSectionTitle")}</p>
            <p className="text-muted-foreground mt-1 text-xs">{t("indexCapabilitiesDisclaimer")}</p>
            <div className="mt-3 grid grid-cols-1 gap-3">
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-strategy" className="text-xs">
                  {t("materializationStrategyLabel")}
                </Label>
                <select
                  id="proj-strategy"
                  className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
                  {...form.register("materializationStrategy")}
                >
                  <option value="CHUNK_LEVEL">CHUNK_LEVEL</option>
                  <option value="DOCUMENT_LEVEL">DOCUMENT_LEVEL</option>
                  <option value="HYBRID">HYBRID</option>
                  <option value="STRUCTURED_SEARCH">STRUCTURED_SEARCH</option>
                </select>
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="border-input size-4 rounded" {...form.register("metadataEnabled")} />
                <span>{t("metadataIndexLabel")}</span>
              </label>
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-embed" className="text-xs">
                  {t("embeddingModelLabel")}
                </Label>
                <Input id="proj-embed" placeholder="mxbai-embed-large" {...form.register("embeddingModelId")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="proj-chunk" className="text-xs">
                  {t("chunkMaxCharsLabel")}
                </Label>
                <Input id="proj-chunk" type="number" {...form.register("chunkMaxChars", { valueAsNumber: true })} />
              </div>
            </div>
          </div>
          {isError ? (
            <p className="text-destructive text-sm" role="alert" data-testid="project-create-error">
              {t("createError")}
            </p>
          ) : null}
          {createWarning ? (
            <p className="text-amber-700 text-sm dark:text-amber-400" role="status" data-testid="project-create-warning">
              {createWarning}
            </p>
          ) : null}
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={isPending}>
              {t("createSubmit")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
