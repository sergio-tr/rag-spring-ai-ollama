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

const schema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(4000).optional(),
});

type FormValues = z.infer<typeof schema>;

type EditProjectDialogProps = {
  project: ProjectSummary;
};

export function EditProjectDialog({ project }: EditProjectDialogProps) {
  const t = useTranslations("Projects");
  const [open, setOpen] = useState(false);
  const patch = usePatchProject();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: project.name,
      description: project.description ?? "",
    },
  });

  useEffect(() => {
    form.reset({
      name: project.name,
      description: project.description ?? "",
    });
  }, [project.id, project.name, project.description, form]);

  async function onSubmit(values: FormValues) {
    try {
      await patch.mutateAsync({
        id: project.id,
        name: values.name,
        description: values.description || null,
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
