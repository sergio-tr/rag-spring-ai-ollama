"use client";

import { MoreVertical } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Suspense, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  inferMainSection,
  settingsTabKeyFromPath,
} from "@/components/layout/context-breadcrumb-logic";
import { NewProjectDialog } from "@/features/projects/components/NewProjectDialog";
import { usePathname, useRouter } from "@/navigation";
import { useAppStore } from "@/store/app.store";
import { cn } from "@/lib/utils";

const SETTINGS_ME_SUMMARY_KEY = ["settings", "me", "summary"] as const;
const SETTINGS_ME_DOCS_PAGE_KEY = ["settings", "me", "documents", 0, 50] as const;

function MenuHint({ children }: Readonly<{ children: ReactNode }>) {
  return <span className="text-muted-foreground mt-0.5 block max-w-[16rem] text-xs font-normal">{children}</span>;
}

function SectionMenuTrigger({ ariaLabel }: Readonly<{ ariaLabel: string }>) {
  return (
    <DropdownMenuTrigger
      type="button"
      className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "shrink-0")}
      aria-label={ariaLabel}
    >
      <MoreVertical className="size-4" aria-hidden />
    </DropdownMenuTrigger>
  );
}

function ProjectsSectionActions() {
  const t = useTranslations("SectionActions");
  const [newProjectOpen, setNewProjectOpen] = useState(false);

  return (
    <>
      <DropdownMenu>
        <SectionMenuTrigger ariaLabel={t("projectsMenuLabel")} />
        <DropdownMenuContent align="end" className="min-w-56">
          <DropdownMenuItem onClick={() => setNewProjectOpen(true)}>{t("newProject")}</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
            <span>{t("deleteAllProjects")}</span>
            <MenuHint>{t("deleteAllProjectsUnavailable")}</MenuHint>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <NewProjectDialog open={newProjectOpen} onOpenChange={setNewProjectOpen} />
    </>
  );
}

function DocumentsSectionActions() {
  const t = useTranslations("SectionActions");
  const qc = useQueryClient();
  const projectId = useAppStore((s) => s.activeProject?.id);

  function refreshList() {
    if (!projectId) return;
    void qc.invalidateQueries({ queryKey: ["project-documents", projectId] });
  }

  return (
    <DropdownMenu>
      <SectionMenuTrigger ariaLabel={t("documentsMenuLabel")} />
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuItem disabled={!projectId} onClick={refreshList}>
          <span className="flex flex-col items-start gap-0">
            <span>{t("refreshDocumentList")}</span>
            {!projectId ? <MenuHint>{t("needsActiveProject")}</MenuHint> : null}
          </span>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("deleteAllDocuments")}</span>
          <MenuHint>{t("deleteAllDocumentsUnavailable")}</MenuHint>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function ChatSectionActionsInner() {
  const t = useTranslations("SectionActions");
  const searchParams = useSearchParams();
  const conversationId = searchParams?.get("conversationId")?.trim() ?? null;
  const activeProject = useAppStore((s) => s.activeProject);

  const needsProject = !activeProject?.id;
  const needsConversation = !conversationId;

  return (
    <DropdownMenu>
      <SectionMenuTrigger ariaLabel={t("chatMenuLabel")} />
      <DropdownMenuContent align="end" className="min-w-60">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="text-muted-foreground font-normal">
            {t("chatMenuPhaseNote")}
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("chatMoveProject")}</span>
          <MenuHint>{needsProject ? t("needsActiveProject") : t("chatActionDeferred")}</MenuHint>
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("chatModel")}</span>
          <MenuHint>{t("chatActionDeferred")}</MenuHint>
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("chatPreset")}</span>
          <MenuHint>{t("chatActionDeferred")}</MenuHint>
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("chatLimitRetrieval")}</span>
          <MenuHint>{t("chatActionDeferred")}</MenuHint>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("chatDelete")}</span>
          <MenuHint>
            {needsConversation ? t("needsActiveConversation") : t("chatDeleteUnavailable")}
          </MenuHint>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function ChatSectionActions() {
  return (
    <Suspense fallback={null}>
      <ChatSectionActionsInner />
    </Suspense>
  );
}

function SettingsSectionActions({ pathname }: Readonly<{ pathname: string }>) {
  const t = useTranslations("SectionActions");
  const router = useRouter();
  const qc = useQueryClient();
  const tabKey = settingsTabKeyFromPath(pathname);

  function reloadView() {
    router.refresh();
  }

  function refreshDataCaches() {
    void qc.invalidateQueries({ queryKey: SETTINGS_ME_SUMMARY_KEY });
    void qc.invalidateQueries({ queryKey: SETTINGS_ME_DOCS_PAGE_KEY });
  }

  function goToAccountExport() {
    router.push("/settings/account#settings-account-export");
  }

  return (
    <DropdownMenu>
      <SectionMenuTrigger ariaLabel={t("settingsMenuLabel")} />
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuItem onClick={reloadView}>{t("reloadSettings")}</DropdownMenuItem>
        {tabKey === "tabData" ? (
          <DropdownMenuItem onClick={refreshDataCaches}>{t("refreshUsageData")}</DropdownMenuItem>
        ) : null}
        {tabKey === "tabAccount" ? (
          <DropdownMenuItem onClick={goToAccountExport}>{t("openAccountExport")}</DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={goToAccountExport}>{t("goToAccountExport")}</DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="flex cursor-not-allowed flex-col items-start opacity-60">
          <span>{t("settingsImportExportDeferred")}</span>
          <MenuHint>{t("settingsFormsDeferredHint")}</MenuHint>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Section-specific “more actions” menu for the main toolbar (three-dot trigger).
 */
export function AppSectionActions() {
  const pathname = usePathname() ?? "/";
  const section = inferMainSection(pathname);

  switch (section) {
    case "projects":
      return <ProjectsSectionActions />;
    case "documents":
      return <DocumentsSectionActions />;
    case "chat":
      return <ChatSectionActions />;
    case "settings":
      return <SettingsSectionActions pathname={pathname} />;
    default:
      return null;
  }
}

/** Toolbar slot wrapper (keeps import stable if Suspense boundaries move later). */
export function AppSectionActionsGate() {
  return <AppSectionActions />;
}
