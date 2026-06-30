"use client";

import { useTranslations } from "next-intl";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { ChatConfigurationPanelContent } from "@/features/chat/components/ChatConfigurationPanelContent";

export function ChatConfigurationDrawer({
  open,
  onOpenChange,
}: Readonly<{
  open: boolean;
  onOpenChange: (open: boolean) => void;
}>) {
  const tChat = useTranslations("Chat");

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        showCloseButton
        className="flex h-[100dvh] w-[min(100vw,26rem)] max-w-[min(100vw,26rem)] flex-col gap-0 overflow-hidden p-0"
      >
        <SheetHeader className="border-border shrink-0 border-b px-4 py-3 text-left">
          <SheetTitle>{tChat("chatConfigPanelTitle")}</SheetTitle>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
          <ChatConfigurationPanelContent />
        </div>
      </SheetContent>
    </Sheet>
  );
}

