"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { ChatConfigurationPanelContent } from "@/features/chat/components/ChatConfigurationPanelContent";

export function ChatConfigurationSidePanel({
  open,
  onClose,
}: Readonly<{
  open: boolean;
  onClose: () => void;
}>) {
  const tChat = useTranslations("Chat");

  if (!open) return null;

  return (
    <aside
      id="chat-configuration-side-panel"
      data-testid="chat-configuration-side-panel"
      className={cn(
        "hidden md:flex",
        "h-full min-h-0 min-w-0 shrink-0 self-stretch flex-col overflow-hidden",
        "border-border border-l bg-background",
        "md:w-[min(100%,28rem)] md:max-w-[32rem]",
      )}
      aria-label={tChat("chatConfigPanelTitle")}
    >
      <header className="border-border shrink-0 border-b px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="font-medium text-sm">{tChat("chatConfigPanelTitle")}</h2>
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground text-xs"
            onClick={onClose}
          >
            {tChat("chatConfigPanelClose")}
          </button>
        </div>
      </header>
      <div className="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-4">
        <ChatConfigurationPanelContent />
      </div>
    </aside>
  );
}

