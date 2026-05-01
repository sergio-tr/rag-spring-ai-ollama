"use client";

import { ChevronRight } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Suspense, useMemo, type ReactNode } from "react";
import { usePathname } from "@/navigation";
import { ProjectVisual } from "@/features/projects/components/ProjectVisual";
import { useConversations } from "@/features/chat/hooks/use-conversations";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { useAppStore } from "@/store/app.store";
import { cn } from "@/lib/utils";
import {
  inferMainSection,
  settingsTabKeyFromPath,
  type MainAppSection,
} from "@/components/layout/context-breadcrumb-logic";

function sectionNavKey(section: MainAppSection): "projects" | "documents" | "chat" | "lab" | "admin" | "settingsPage" | null {
  switch (section) {
    case "projects":
      return "projects";
    case "documents":
      return "documents";
    case "chat":
      return "chat";
    case "lab":
      return "lab";
    case "admin":
      return "admin";
    case "settings":
      return "settingsPage";
    default:
      return null;
  }
}

function settingsTabLabel(
  t: ReturnType<typeof useTranslations>,
  key: NonNullable<ReturnType<typeof settingsTabKeyFromPath>>,
): string {
  switch (key) {
    case "tabAccount":
      return t("tabAccount");
    case "tabData":
      return t("tabData");
    case "tabGeneral":
      return t("tabGeneral");
    case "tabPresets":
      return t("tabPresets");
    case "tabProject":
      return t("tabProject");
    case "tabUser":
      return t("tabUser");
    default:
      return t("tabGeneral");
  }
}

function useChatSegmentTitle(projectId: string | undefined, conversationId: string | null) {
  const q = useConversations(projectId);
  return useMemo(() => {
    if (!conversationId || !projectId) {
      return { label: null as string | null, pending: false, needsFallback: false };
    }
    if (q.isLoading) {
      return { label: null, pending: true, needsFallback: false };
    }
    if (q.isError || !q.data) {
      return { label: null, pending: false, needsFallback: true };
    }
    const row = q.data.find((c) => c.id === conversationId);
    const trimmed = row?.title?.trim();
    if (!trimmed) {
      return { label: null, pending: false, needsFallback: true };
    }
    return { label: trimmed, pending: false, needsFallback: false };
  }, [conversationId, projectId, q.data, q.isError, q.isLoading]);
}

function AppContextBreadcrumbFallback() {
  const tCtx = useTranslations("Context");
  return (
    <div
      aria-busy="true"
      className="text-muted-foreground min-w-0 flex-1 truncate text-sm animate-pulse"
    >
      {tCtx("breadcrumbLoading")}
    </div>
  );
}

/**
 * Primary location breadcrumb: active project (or none), then route section and contextual labels.
 * Requires Suspense when using URL search params (chat conversation id).
 */
export function AppContextBreadcrumb({ className }: Readonly<{ className?: string }>) {
  const tNav = useTranslations("Nav");
  const tCtx = useTranslations("Context");
  const tSettings = useTranslations("Settings");
  const pathname = usePathname() ?? "/";
  const searchParams = useSearchParams();
  const conversationId = searchParams?.get("conversationId")?.trim() || null;

  const activeProject = useAppStore((s) => s.activeProject);
  const { data: projectPage, isLoading: projectsLoading } = useProjectList(0, 64);

  const resolvedProject =
    !activeProject?.id ? null : (projectPage?.items.find((p) => p.id === activeProject.id) ?? null);

  const displayProjectName = resolvedProject?.name ?? activeProject?.name ?? null;
  const iconKey = resolvedProject?.iconKey ?? activeProject?.iconKey;
  const colorHex = resolvedProject?.colorHex ?? activeProject?.colorHex;

  const projectLoading = Boolean(activeProject && projectsLoading && !resolvedProject);
  const section = inferMainSection(pathname);
  const sectionKey = sectionNavKey(section);
  const settingsTabKey = section === "settings" ? settingsTabKeyFromPath(pathname) : null;

  const chatTitle = useChatSegmentTitle(activeProject?.id, conversationId);

  const items: { key: string; node: ReactNode }[] = [];

  // Project (always first)
  if (!activeProject) {
    items.push({
      key: "project-none",
      node: (
        <span className="text-muted-foreground font-medium">{tCtx("noProjectSelected")}</span>
      ),
    });
  } else if (projectLoading) {
    items.push({
      key: "project-loading",
      node: (
        <span className="text-muted-foreground animate-pulse">{tCtx("loadingProject")}</span>
      ),
    });
  } else {
    items.push({
      key: `project-${activeProject.id}`,
      node: (
        <span className="flex min-w-0 items-center gap-2 font-medium text-foreground">
          <ProjectVisual
            iconKey={iconKey}
            colorHex={colorHex}
            iconClassName="size-4 shrink-0"
            dotClassName="size-2.5 shrink-0 rounded-full border border-border"
          />
          <span className="truncate">{displayProjectName ?? tCtx("unnamedProject")}</span>
        </span>
      ),
    });
  }

  // Route section (skip unknown to reduce noise on odd paths)
  if (sectionKey) {
    items.push({
      key: `section-${section}`,
      node: <span className="truncate font-medium">{tNav(sectionKey)}</span>,
    });
  }

  // Chat conversation title
  if (section === "chat" && conversationId) {
    let chatLabel: string;
    if (chatTitle.pending) {
      chatLabel = tCtx("loadingChatTitle");
    } else if (chatTitle.needsFallback || !chatTitle.label) {
      chatLabel = tCtx("untitledChat");
    } else {
      chatLabel = chatTitle.label;
    }
    items.push({
      key: `chat-${conversationId}`,
      node: <span className="text-muted-foreground max-w-[12rem] truncate md:max-w-md">{chatLabel}</span>,
    });
  }

  // Settings tab
  if (section === "settings" && settingsTabKey) {
    items.push({
      key: `settings-${settingsTabKey}`,
      node: (
        <span className="text-muted-foreground truncate">
          {settingsTabLabel(tSettings, settingsTabKey)}
        </span>
      ),
    });
  }

  return (
    <nav aria-label={tCtx("breadcrumbNavLabel")} className={cn("min-w-0 flex-1", className)}>
      <ol className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm">
        {items.map((item, index) => (
          <li key={item.key} className="flex min-w-0 items-center gap-1">
            {index > 0 ? (
              <ChevronRight className="text-muted-foreground size-3.5 shrink-0" aria-hidden />
            ) : null}
            {item.node}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function AppContextBreadcrumbGate(props: Readonly<{ className?: string }>) {
  return (
    <Suspense fallback={<AppContextBreadcrumbFallback />}>
      <AppContextBreadcrumb {...props} />
    </Suspense>
  );
}
