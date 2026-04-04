"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
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
import { useDeleteProject } from "@/features/projects/hooks/use-projects";
import { cn } from "@/lib/utils";
import type { ProjectSummary } from "@/types/api";

type DeleteProjectDialogProps = {
  project: ProjectSummary;
};

export function DeleteProjectDialog({ project }: DeleteProjectDialogProps) {
  const t = useTranslations("Projects");
  const [open, setOpen] = useState(false);
  const del = useDeleteProject();

  async function confirmDelete() {
    try {
      await del.mutateAsync(project.id);
      setOpen(false);
    } catch {
      /* mutation error state */
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        type="button"
        className={cn(buttonVariants({ variant: "destructive", size: "sm" }))}
      >
        {t("delete")}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("deleteTitle")}</DialogTitle>
          <DialogDescription>{t("deleteDescription", { name: project.name })}</DialogDescription>
        </DialogHeader>
        {del.isError && (
          <p className="text-destructive text-sm" role="alert">
            {t("deleteError")}
          </p>
        )}
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" variant="destructive" disabled={del.isPending} onClick={() => void confirmDelete()}>
            {t("deleteConfirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
