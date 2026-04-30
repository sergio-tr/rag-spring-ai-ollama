"use client";

import { PanelRightClose, PanelRightOpen } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { AppSidebar } from "@/components/layout/AppSidebar";
import { CollapsiblePanel } from "@/components/layout/CollapsiblePanel";
import { ThemeLanguageMenu } from "@/components/layout/ThemeLanguageMenu";
import { SessionExpiredBridge } from "@/components/auth/SessionExpiredBridge";
import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { ExplainabilityPanel } from "@/features/rag/ExplainabilityPanel";
import { cn } from "@/lib/utils";
import { useAppStore } from "@/store/app.store";
import { usePathname, useRouter } from "@/navigation";

type AppShellProps = {
  children: React.ReactNode;
  panelBody: React.ReactNode;
};

export function AppShell({ children, panelBody }: Readonly<AppShellProps>) {
  const tNav = useTranslations("Nav");
  const router = useRouter();
  const pathname = usePathname();
  const [panelOpen, setPanelOpen] = useState(true);
  const isChat = /\/chat(\/|$)/.test(pathname ?? "");
  const activeProject = useAppStore((s) => s.activeProject);

  async function signOut() {
    await clearSessionCookie();
    router.push("/login");
    router.refresh();
  }

  return (
    <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-background">
      <SessionExpiredBridge />
      <header className="flex h-12 shrink-0 items-center justify-end gap-2 border-border border-b bg-background px-3">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-expanded={panelOpen}
          aria-label={panelOpen ? "Hide side panel" : "Show side panel"}
          onClick={() => setPanelOpen((o) => !o)}
        >
          {panelOpen ? <PanelRightClose className="size-4" /> : <PanelRightOpen className="size-4" />}
        </Button>
        <ThemeLanguageMenu />
        <Button type="button" variant="ghost" size="sm" onClick={() => void signOut()}>
          {tNav("signOut")}
        </Button>
      </header>
      {activeProject ? (
        <div className="text-muted-foreground border-border border-b bg-muted/30 px-3 py-1 text-center text-xs">
          {tNav("activeProjectLabel")}: <span className="text-foreground font-medium">{activeProject.name}</span>
        </div>
      ) : null}
      <div className="flex min-h-0 min-w-0 flex-1">
        <AppSidebar />
        <main className="min-w-0 flex-1 overflow-y-auto">
          <div
            className={cn(
              "mx-auto px-4 py-6 md:px-8",
              isChat ? "max-w-none w-full" : "max-w-5xl",
            )}
          >
            {children}
          </div>
        </main>
        <CollapsiblePanel open={panelOpen}>
          {isChat ? <ExplainabilityPanel /> : panelBody}
        </CollapsiblePanel>
      </div>
    </div>
  );
}
