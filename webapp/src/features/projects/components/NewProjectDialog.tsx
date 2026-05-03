"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useState } from "react";
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

const schema = z.object({
  name: z.string().min(1).max(120),
  description: z.string().max(2000).optional(),
});

type FormValues = z.infer<typeof schema>;

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
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const controlled = controlledOpen !== undefined && onOpenChange !== undefined;
  const open = controlled ? controlledOpen : uncontrolledOpen;
  const setOpen = controlled ? onOpenChange : setUncontrolledOpen;
  const create = useCreateProject();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", description: "" },
  });

  async function onSubmit(values: FormValues) {
    try {
      await create.mutateAsync({
        name: values.name,
        description: values.description || undefined,
      });
      setOpen(false);
      form.reset();
    } catch {
      // Error surfaced via mutation state below
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
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
          {create.isError && (
            <p className="text-destructive text-sm" role="alert">
              {t("createError")}
            </p>
          )}
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {t("createSubmit")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
