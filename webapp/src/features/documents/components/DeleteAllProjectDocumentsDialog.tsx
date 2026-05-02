"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDeleteAllProjectDocuments } from "@/features/documents/hooks/use-project-documents";
import { getSafeApiErrorMessage } from "@/lib/api-client";

type DeleteAllProjectDocumentsDialogProps = Readonly<{
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string | undefined;
  projectName: string | undefined;
}>;

/** Confirmation phrase — intentionally ASCII so EN/ES share one gate (operators paste reliably). */
export const DELETE_ALL_PROJECT_DOCUMENTS_PHRASE = "DELETE ALL DOCUMENTS";

export function DeleteAllProjectDocumentsDialog({
  open,
  onOpenChange,
  projectId,
  projectName,
}: DeleteAllProjectDocumentsDialogProps) {
  const t = useTranslations("Documents");
  const delAll = useDeleteAllProjectDocuments(projectId);
  const [phrase, setPhrase] = useState("");

  function resetLocalState() {
    setPhrase("");
    delAll.reset();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) resetLocalState();
        onOpenChange(next);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("deleteAllTitle")}</DialogTitle>
          <DialogDescription>{t("deleteAllDescription", { name: projectName ?? "—" })}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="delete-all-phrase">{t("deleteAllPhraseLabel")}</Label>
          <Input
            id="delete-all-phrase"
            autoComplete="off"
            value={phrase}
            placeholder={DELETE_ALL_PROJECT_DOCUMENTS_PHRASE}
            onChange={(e) => setPhrase(e.target.value)}
          />
          <p className="text-muted-foreground text-xs">{t("deleteAllTechnicalNote")}</p>
        </div>
        {delAll.isError ? (
          <p className="text-destructive text-sm" role="alert">
            {getSafeApiErrorMessage(delAll.error)}
          </p>
        ) : null}
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t("deleteAllCancel")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={
              delAll.isPending ||
              phrase.trim() !== DELETE_ALL_PROJECT_DOCUMENTS_PHRASE ||
              !projectId
            }
            onClick={() =>
              void delAll.mutateAsync().then(() => {
                resetLocalState();
                onOpenChange(false);
              })
            }
          >
            {t("deleteAllConfirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
