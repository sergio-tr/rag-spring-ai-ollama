"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { useMoveConversation } from "@/features/chat/hooks/use-conversations";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { useTranslations } from "next-intl";
import { Fragment, useId, useMemo, useState } from "react";

type MoveConversationDialogProps = {
  sourceProjectId: string;
  conversationId: string | null;
};

export function MoveConversationDialog({
  sourceProjectId,
  conversationId,
}: Readonly<MoveConversationDialogProps>) {
  const t = useTranslations("Chat");
  const tProj = useTranslations("Projects");
  const selectId = useId();
  const [open, setOpen] = useState(false);
  const [destinationId, setDestinationId] = useState("");
  const move = useMoveConversation();

  const { data: projectData } = useProjectList(0, 64);
  const destinations = useMemo(
    () => projectData?.items.filter((p) => p.id !== sourceProjectId) ?? [],
    [projectData?.items, sourceProjectId],
  );

  async function confirmMove() {
    if (!conversationId || !destinationId) return;
    const dest = destinations.find((p) => p.id === destinationId);
    if (!dest) return;
    try {
      await move.mutateAsync({
        sourceProjectId,
        conversationId,
        destinationProjectId: destinationId,
        destinationProjectName: dest.name,
      });
      setOpen(false);
      setDestinationId("");
    } catch {
      /* surfaced via move.isError */
    }
  }

  const disabled =
    !conversationId || destinations.length === 0 || move.isPending || !destinationId;

  return (
    <Fragment>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-full"
        disabled={!conversationId}
        onClick={() => setOpen(true)}
      >
        {t("moveConversation")}
      </Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) {
            setDestinationId("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("moveConversationTitle")}</DialogTitle>
            <DialogDescription>{t("moveConversationDescription")}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor={selectId}>{t("moveDestinationLabel")}</Label>
            <select
              id={selectId}
              className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
              value={destinationId}
              onChange={(e) => setDestinationId(e.target.value)}
              disabled={move.isPending || destinations.length === 0}
            >
              <option value="">{t("moveDestinationPlaceholder")}</option>
              {destinations.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          {move.isError && (
            <p className="text-destructive text-sm" role="alert">
              {t("moveError")}
            </p>
          )}
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {tProj("cancel")}
            </Button>
            <Button type="button" disabled={disabled} onClick={() => void confirmMove()}>
              {t("moveConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Fragment>
  );
}
