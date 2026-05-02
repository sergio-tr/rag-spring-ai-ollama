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
import { useDeleteConversation } from "@/features/chat/hooks/use-conversations";
import { getSafeApiErrorMessage } from "@/lib/api-client";

export type DeleteConversationDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string | undefined;
  conversationId: string | undefined;
  conversationTitle: string;
  onDeleted?: () => void;
};

export function DeleteConversationDialog({
  open,
  onOpenChange,
  projectId,
  conversationId,
  conversationTitle,
  onDeleted,
}: Readonly<DeleteConversationDialogProps>) {
  const t = useTranslations("Chat");
  const del = useDeleteConversation(projectId);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  function handleOpenChange(next: boolean) {
    setErrorMsg(null);
    onOpenChange(next);
  }

  async function confirm() {
    if (!conversationId) return;
    setErrorMsg(null);
    try {
      await del.mutateAsync(conversationId);
      onDeleted?.();
      handleOpenChange(false);
    } catch (e) {
      setErrorMsg(getSafeApiErrorMessage(e));
    }
  }

  const ready = Boolean(projectId && conversationId);
  const titleLabel = conversationTitle.trim() || t("deleteConversationUntitled");

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent data-testid="chat-delete-confirm-dialog">
        <DialogHeader>
          <DialogTitle>{t("deleteConversationTitle")}</DialogTitle>
          <DialogDescription>
            {t("deleteConversationDescription", { title: titleLabel })}
          </DialogDescription>
        </DialogHeader>
        {errorMsg ? (
          <p className="text-destructive text-sm" role="alert">
            {errorMsg}
          </p>
        ) : null}
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t("deleteConversationCancel")}
          </Button>
          <Button
            type="button"
            data-testid="chat-delete-confirm-button"
            variant="destructive"
            disabled={del.isPending || !ready}
            onClick={() => void confirm()}
          >
            {t("deleteConversationConfirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
