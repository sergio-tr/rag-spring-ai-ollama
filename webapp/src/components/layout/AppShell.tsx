"use client";

import { History, Menu } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { AppSidebar } from "@/components/layout/AppSidebar";
import { AppContextBreadcrumbGate } from "@/components/layout/AppContextBreadcrumb";
import { AppSectionActionsGate } from "@/components/layout/AppSectionActions";
import { ActivityHelpSheet } from "@/components/layout/ActivityHelpSheet";
import { ThemeLanguageMenu } from "@/components/layout/ThemeLanguageMenu";
import { SessionExpiredBridge } from "@/components/auth/SessionExpiredBridge";
import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { cn } from "@/lib/utils";
import { usePathname, useRouter } from "@/navigation";
import { SidebarResizeHandle } from "@/components/layout/SidebarResizeHandle";
import { useSidebarShell } from "@/components/layout/use-sidebar-shell";
import { RAIL_WIDTH_PX } from "@/components/layout/sidebar-layout";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";

type AppShellProps = {
  children: React.ReactNode;
  panelBody: React.ReactNode;
};

export function AppShell({ children, panelBody }: Readonly<AppShellProps>) {
  const tNav = useTranslations("Nav");
  const tActivity = useTranslations("Activity");
  const router = useRouter();
  const pathname = usePathname();
  const [activityOpen, setActivityOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const isChat = /\/chat(\/|$)/.test(pathname ?? "");
  const { railCollapsed, expandedWidthPx, toggleRailCollapsed, applyResizeDelta } = useSidebarShell();

  const sidebarWidthPx = railCollapsed ? RAIL_WIDTH_PX : expandedWidthPx;

  async function signOut() {
    await clearSessionCookie();
    router.push("/login");
    router.refresh();
  }

  return (
    <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-background">
      <SessionExpiredBridge />
      <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden">
        <div
          className="relative hidden h-full min-h-0 shrink-0 md:flex md:flex-col"
          style={{ width: sidebarWidthPx }}
        >
          <AppSidebar
            variant="desktop"
            railCollapsed={railCollapsed}
            onToggleRailCollapsed={toggleRailCollapsed}
            onSignOut={() => void signOut()}
          />
        </div>
        <SidebarResizeHandle
          className="hidden shrink-0 md:flex"
          disabled={railCollapsed}
          ariaLabel={tNav("resizeSidebar")}
          onDragDelta={applyResizeDelta}
        />
        <main className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <header
            data-testid="app-main-toolbar"
            className="flex shrink-0 items-center gap-2 border-border border-b bg-background px-3 py-2 md:px-4"
          >
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              className="shrink-0 md:hidden"
              aria-expanded={mobileNavOpen}
              aria-controls="app-sidebar-drawer"
              onClick={() => setMobileNavOpen(true)}
            >
              <Menu className="size-4" aria-hidden />
              <span className="sr-only">{tNav("openNavigation")}</span>
            </Button>
            <div className="flex min-w-0 flex-1 items-center gap-1">
              <AppContextBreadcrumbGate className="min-w-0" />
              <AppSectionActionsGate />
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-expanded={activityOpen}
                aria-controls="activity-help-sheet"
                aria-label={activityOpen ? tActivity("hideSheet") : tActivity("showSheet")}
                onClick={() => setActivityOpen((o) => !o)}
              >
                <History className="size-4" aria-hidden />
              </Button>
              <ThemeLanguageMenu />
            </div>
          </header>
          <div
            data-testid="app-main-scroll"
            data-scroll-mode={isChat ? "chat-locked" : "document"}
            className={cn(
              "min-h-0 flex-1 min-w-0",
              isChat
                ? "flex flex-col overflow-hidden"
                : "overflow-x-hidden overflow-y-auto",
            )}
          >
            <div
              className={cn(
                "mx-auto flex w-full min-w-0 flex-col",
                isChat
                  ? "h-full min-h-0 flex-1 px-4 md:px-8 max-w-6xl"
                  : "px-4 py-6 md:px-8 max-w-5xl",
              )}
            >
              {children}
            </div>
          </div>
        </main>
        <ActivityHelpSheet
          open={activityOpen}
          onOpenChange={setActivityOpen}
          isChatRoute={isChat}
          intro={panelBody}
        />
      </div>

      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetContent
          side="left"
          showCloseButton
          className="flex h-[100dvh] w-[min(88vw,360px)] max-w-[85vw] flex-col gap-0 border-r p-0"
        >
          <SheetTitle className="sr-only">{tNav("navigationDrawerTitle")}</SheetTitle>
          <AppSidebar
            variant="drawer"
            onNavigate={() => setMobileNavOpen(false)}
            onSignOut={() => void signOut()}
          />
        </SheetContent>
      </Sheet>
    </div>
  );
}
