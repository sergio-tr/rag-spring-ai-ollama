"use client";

import { MoreVertical } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { ChatConfigurationDrawer } from "@/features/chat/components/ChatConfigurationDrawer";
import { useMediaQuery } from "@/features/chat/hooks/use-media-query";
import { useChatConfigurationPanelStore } from "@/features/chat/store/chat-configuration-panel.store";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { cn } from "@/lib/utils";

/** Toolbar trigger for Chat configuration panel (⋮). */
export function ChatToolbarOverflowMenu() {
  const t = useTranslations("SectionActions");
  const api = useChatToolbarStore((s) => s.api);
  const isDesktop = useMediaQuery("(min-width: 768px)");
  const [mobileOpen, setMobileOpen] = useState(false);
  const panelOpen = useChatConfigurationPanelStore((s) => s.open);

  useEffect(() => {
    useChatConfigurationPanelStore.getState().hydrateFromStorage();
  }, []);

  const disabled = !api?.projectId || !api?.conversationId;

  return (
    <>
      <button
        type="button"
        data-testid="chat-config-trigger"
        className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "shrink-0")}
        aria-label={t("chatMenuLabel")}
        aria-expanded={isDesktop ? panelOpen : mobileOpen}
        aria-controls={isDesktop ? "chat-configuration-side-panel" : "chat-configuration-drawer"}
        disabled={disabled}
        onClick={() => {
          if (!api?.projectId || !api?.conversationId) return;
          if (isDesktop) {
            useChatConfigurationPanelStore.getState().toggle();
          } else {
            setMobileOpen(true);
          }
        }}
      >
        <MoreVertical className="size-4" aria-hidden />
      </button>

      <div className="md:hidden" id="chat-configuration-drawer">
        <ChatConfigurationDrawer open={mobileOpen} onOpenChange={setMobileOpen} />
      </div>
    </>
  );
}

