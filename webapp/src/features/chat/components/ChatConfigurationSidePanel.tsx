"use client";

import { cn } from "@/lib/utils";
import { ChatConfigurationPanelContent } from "@/features/chat/components/ChatConfigurationPanelContent";

export function ChatConfigurationSidePanel({
  open,
  onClose,
}: Readonly<{
  open: boolean;
  onClose: () => void;
}>) {
  if (!open) return null;

  return (
    <aside
      id="chat-configuration-side-panel"
      data-testid="chat-configuration-side-panel"
      className={cn(
        "hidden md:flex",
        "min-h-0 w-[min(100%,22rem)] max-w-[min(100%,22rem)] shrink-0 flex-col overflow-hidden",
        "border-border border-l bg-background",
      )}
      aria-label="Chat configuration"
    >
      <header className="border-border shrink-0 border-b px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="font-medium text-sm">Chat configuration</h2>
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground text-xs"
            onClick={onClose}
          >
            Close
          </button>
        </div>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
        <ChatConfigurationPanelContent />
      </div>
    </aside>
  );
}

