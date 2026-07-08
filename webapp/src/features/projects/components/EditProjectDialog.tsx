"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { usePatchProject } from "@/features/projects/hooks/use-projects";
import { cn } from "@/lib/utils";
import type { ProjectSummary } from "@/types/api";
import { Link } from "@/navigation";

const PROJECT_ICONS = [
  "folder",
  "briefcase",
  "star",
  "code",
  "book",
  "chat",
  "lab",
  "rocket",
  "shield",
] as const;

const schema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(4000).optional(),
  projectPrompt: z.string().max(50_000).optional(),
  colorHex: z
    .string()
    .optional()
    .refine((s) => !s || /^#([0-9A-Fa-f]{6})$/.test(s), "Invalid color"),
  iconKey: z.string().max(64).optional(),
});

type FormValues = z.infer<typeof schema>;

type EditProjectDialogProps = {
  project: ProjectSummary;
};

export function EditProjectDialog({ project }: EditProjectDialogProps) {
  const t = useTranslations("Projects");
  const tSettings = useTranslations("Settings");
  const [open, setOpen] = useState(false);
  const patch = usePatchProject();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: project.name,
      description: project.description ?? "",
      projectPrompt: project.projectPrompt ?? "",
      colorHex: project.colorHex ?? "#6b7280",
      iconKey: project.iconKey ?? "",
    },
  });

  useEffect(() => {
    form.reset({
      name: project.name,
      description: project.description ?? "",
      projectPrompt: project.projectPrompt ?? "",
      colorHex: project.colorHex ?? "#6b7280",
      iconKey: project.iconKey ?? "",
    });
  }, [project.id, project.name, project.description, project.projectPrompt, project.colorHex, project.iconKey, form]);

  async function onSubmit(values: FormValues) {
    try {
      await patch.mutateAsync({
        id: project.id,
        name: values.name,
        description: values.description || null,
        projectPrompt: values.projectPrompt?.trim() || null,
        colorHex: values.colorHex || undefined,
        iconKey: values.iconKey?.trim() || undefined,
      });
      setOpen(false);
    } catch {
      /* surfaced below */
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger type="button" className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
        {t("edit")}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("editTitle")}</DialogTitle>
          <DialogDescription>{t("editDescription")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-2">
            <Label htmlFor={`edit-proj-name-${project.id}`}>{t("name")}</Label>
            <Input id={`edit-proj-name-${project.id}`} {...form.register("name")} />
            {form.formState.errors.name && (
              <p className="text-destructive text-sm" role="alert">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor={`edit-proj-desc-${project.id}`}>{t("description")}</Label>
            <Input id={`edit-proj-desc-${project.id}`} {...form.register("description")} />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor={`edit-proj-prompt-${project.id}`}>{tSettings("projectPromptLabel")}</Label>
            <p className="text-muted-foreground text-xs">{tSettings("projectPromptHint")}</p>
            <textarea
              id={`edit-proj-prompt-${project.id}`}
              data-testid="edit-project-prompt"
              className="border-input bg-background min-h-24 w-full rounded-md border px-3 py-2 text-sm"
              maxLength={50_000}
              {...form.register("projectPrompt")}
            />
          </div>
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor={`edit-proj-color-${project.id}`}>{t("projectColor")}</Label>
              <Input
                id={`edit-proj-color-${project.id}`}
                type="color"
                className="h-10 w-14 cursor-pointer p-1"
                {...form.register("colorHex")}
              />
            </div>
            <div className="flex min-w-[180px] flex-col gap-2">
              <Label htmlFor={`edit-proj-icon-${project.id}`}>{t("projectIcon")}</Label>
              <select
                id={`edit-proj-icon-${project.id}`}
                className="border-input bg-background h-10 w-full rounded-md border px-3 text-sm"
                {...form.register("iconKey")}
              >
                <option value="">{t("projectIconNone")}</option>
                {PROJECT_ICONS.map((k) => (
                  <option key={k} value={k}>
                    {k}
                  </option>
                ))}
              </select>
            </div>
          </div>
          {form.formState.errors.colorHex && (
            <p className="text-destructive text-sm" role="alert">
              {form.formState.errors.colorHex.message}
            </p>
          )}
          <p className="text-muted-foreground text-xs">
            {t("editModelConfigHint")}{" "}
            <Link href="/settings/project" className="text-primary underline-offset-4 hover:underline">
              {t("editModelConfigAction")}
            </Link>
          </p>
          {patch.isError && (
            <p className="text-destructive text-sm" role="alert">
              {t("editError")}
            </p>
          )}
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={patch.isPending}>
              {t("editSubmit")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
