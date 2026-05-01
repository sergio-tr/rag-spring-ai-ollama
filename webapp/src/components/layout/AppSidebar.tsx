"use client";

import {
  ChevronLeft,
  ChevronRight,
  FileText,
  FlaskConical,
  FolderKanban,
  LogOut,
  MessageSquare,
  Search,
  Settings,
  Shield,
  Trash2,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { usePathname, Link, useRouter } from "@/navigation";
import { cn } from "@/lib/utils";
import { Button, buttonVariants } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useEffect, useMemo, useState } from "react";
import { apiFetch, authApiPath } from "@/lib/api-client";
import { useProjectList, useActivateProject } from "@/features/projects/hooks/use-projects";
import { DeleteConversationDialog } from "@/features/chat/components/DeleteConversationDialog";
import { useConversations, useCreateConversation } from "@/features/chat/hooks/use-conversations";
import { useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";
import { fetchLatestConversationId } from "@/features/projects/lib/open-project-in-chat";
import {
  buildProjectScopedChatHref,
  buildProjectScopedDocumentsHref,
} from "@/features/projects/lib/open-project-navigation";
import { NewProjectDialog } from "@/features/projects/components/NewProjectDialog";
import { ProjectVisual } from "@/features/projects/components/ProjectVisual";
import { getStoredUserRole, setStoredUserRole } from "@/lib/user-role";
import type { MeResponse } from "@/types/api";
import { Suspense } from "react";
import {
  patchSidebarPersistence,
  readSidebarPersistence,
  type SidebarPersistence,
} from "@/components/layout/sidebar-persistence";

const primaryLinks = [
  { href: "/projects" as const, key: "projects" as const, icon: FolderKanban },
  { href: "/documents" as const, key: "documents" as const, icon: FileText },
  { href: "/chat" as const, key: "chat" as const, icon: MessageSquare },
  { href: "/lab" as const, key: "lab" as const, icon: FlaskConical },
  { href: "/admin" as const, key: "admin" as const, icon: Shield },
];

export type AppSidebarChromeProps = Readonly<{
  variant?: "desktop" | "drawer";
  railCollapsed?: boolean;
  onToggleRailCollapsed?: () => void;
  /** Called after in-sidebar navigation (e.g. close mobile drawer). */
  onNavigate?: () => void;
  /** Sign out (session clear + redirect); rendered in sidebar footer when provided. */
  onSignOut?: () => void;
}>;

export function AppSidebar(props?: AppSidebarChromeProps) {
  return (
    <Suspense
      fallback={
        <aside className="flex h-full w-[260px] shrink-0 flex-col border-border border-r bg-sidebar" />
      }
    >
      <AppSidebarContent variant="desktop" {...props} />
    </Suspense>
  );
}

function AppSidebarContent(props?: AppSidebarChromeProps) {
  const {
    variant = "desktop",
    railCollapsed = false,
    onToggleRailCollapsed,
    onNavigate,
    onSignOut,
  } = props ?? {};
  const tNav = useTranslations("Nav");
  const tChat = useTranslations("Chat");
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const selectedConversationId = searchParams?.get("conversationId") ?? null;

  const [role, setRole] = useState(() => getStoredUserRole());
  const canSeeAdmin = role === "ADMIN";

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const me = await apiFetch<MeResponse>(authApiPath("/me"));
        if (cancelled) return;
        const nextRole = me.roleName ?? null;
        setRole(nextRole);
        setStoredUserRole(nextRole);
      } catch {
        // Ignore: anonymous sessions and API failures should not block navigation rendering.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const activeProject = useAppStore((s) => s.activeProject);
  const activateProject = useActivateProject();

  const { data: projectData, isLoading: projectsLoading, isError: projectsError } = useProjectList(0, 64);
  const projects = useMemo(() => projectData?.items ?? [], [projectData?.items]);
  const projectsTotal = projectData?.total ?? projects.length;

  useEffect(() => {
    if (!activeProject?.id || projectsLoading) return;
    if (!projects.some((p) => p.id === activeProject.id)) {
      useAppStore.getState().setActiveProject(null);
    }
  }, [activeProject?.id, projects, projectsLoading]);

  const [initialPersisted] = useState<SidebarPersistence>(() => readSidebarPersistence());
  const [projectsCollapsed, setProjectsCollapsed] = useState(initialPersisted.projectsCollapsed);
  const [expandedProjectIds, setExpandedProjectIds] = useState<string[]>(initialPersisted.expandedProjectIds);
  const expandedSet = useMemo(() => new Set(expandedProjectIds), [expandedProjectIds]);

  function toggleProjectsCollapsed() {
    setProjectsCollapsed((v) => {
      const next = !v;
      patchSidebarPersistence({ projectsCollapsed: next });
      return next;
    });
  }

  function toggleProjectExpanded(projectId: string) {
    setExpandedProjectIds((prev) => {
      const next = prev.includes(projectId) ? prev.filter((id) => id !== projectId) : [...prev, projectId];
      patchSidebarPersistence({ expandedProjectIds: next });
      return next;
    });
  }

  async function activate(p: ProjectSummary) {
    await activateProject.mutateAsync({
      id: p.id,
      name: p.name,
      iconKey: p.iconKey,
      colorHex: p.colorHex,
    });
  }

  async function openProjectInChat(p: ProjectSummary) {
    await activateProject.mutateAsync({
      id: p.id,
      name: p.name,
      iconKey: p.iconKey,
      colorHex: p.colorHex,
    });
    const convId = await fetchLatestConversationId(queryClient, p.id);
    router.push(buildProjectScopedChatHref(p.id, convId));
  }

  function handleConversationDeleted(projectId: string, deletedConversationId: string) {
    const urlPid = searchParams?.get("projectId")?.trim() ?? null;
    if (
      pathname?.includes("/chat") &&
      selectedConversationId === deletedConversationId &&
      urlPid === projectId
    ) {
      router.push(buildProjectScopedChatHref(projectId, null));
    }
  }

  const createConversation = useCreateConversation(activeProject?.id);

  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState("");

  const searchQuery = searchText.trim().toLowerCase();

  const showExpandedChrome = !railCollapsed || variant === "drawer";

  return (
    <aside
      id={variant === "drawer" ? "app-sidebar-drawer" : "app-sidebar"}
      className={cn(
        "flex h-full min-h-0 w-full shrink-0 flex-col overflow-x-hidden bg-sidebar text-sidebar-foreground",
        variant === "desktop" ? "border-border border-r" : "",
      )}
    >
      <div className="flex h-14 shrink-0 items-center gap-1 border-border border-b px-2">
        {variant === "desktop" && onToggleRailCollapsed ? (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="shrink-0"
            aria-expanded={!railCollapsed}
            aria-controls="app-sidebar"
            aria-label={railCollapsed ? tNav("sidebarExpand") : tNav("sidebarCollapse")}
            onClick={onToggleRailCollapsed}
          >
            {railCollapsed ? (
              <ChevronRight className="size-4" aria-hidden />
            ) : (
              <ChevronLeft className="size-4" aria-hidden />
            )}
          </Button>
        ) : (
          <span className="w-0 shrink-0" aria-hidden />
        )}
        <Link
          href="/projects"
          className={cn(
            "flex min-w-0 flex-1 items-center gap-2 rounded-md py-1",
            railCollapsed && variant === "desktop" ? "justify-center px-0" : "px-1",
          )}
          onClick={() => onNavigate?.()}
        >
          <div className="bg-sidebar-accent text-sidebar-accent-foreground flex size-7 shrink-0 items-center justify-center rounded-md text-xs font-semibold">
            RC
          </div>
          <span
            className={cn(
              "truncate font-semibold text-sm tracking-tight",
              railCollapsed && variant === "desktop" && "sr-only",
            )}
          >
            RAG Console
          </span>
        </Link>
      </div>

      <div className="border-border border-b p-2" hidden={!showExpandedChrome}>
        <div className="flex flex-col gap-2">
          <NewProjectDialog triggerClassName="w-full" />
          <Button
            type="button"
            size="sm"
            disabled={!activeProject || createConversation.isPending}
            onClick={async () => {
              if (!activeProject?.id) return;
              const c = await createConversation.mutateAsync();
              router.push(buildProjectScopedChatHref(activeProject.id, c.id));
            }}
          >
            {tChat("newConversation")}
          </Button>
          {!activeProject ? <p className="text-muted-foreground px-1 text-xs">{tChat("newConversationDisabledHint")}</p> : null}
          <Dialog
            open={searchOpen}
            onOpenChange={(o) => {
              setSearchOpen(o);
              if (!o) setSearchText("");
            }}
          >
            <DialogTrigger
              render={
                <button
                  type="button"
                  className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                />
              }
            >
              <Search className="mr-2 size-4" aria-hidden />
              {tChat("searchConversations")}
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>{tChat("searchConversations")}</DialogTitle>
              </DialogHeader>
              <Input
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder={tChat("chatTitlePlaceholder")}
              />
              <SearchChatsBody
                projects={projects}
                activeProjectId={activeProject?.id ?? null}
                expandedProjectIds={expandedProjectIds}
                query={searchQuery}
                activateProject={activate}
                onConversationDeleted={handleConversationDeleted}
                onPickConversation={(projectId, conversationId) => {
                  if (activeProject?.id !== projectId) {
                    // Activation happens in the picker; this is just a safety net for local state.
                  }
                  router.push(buildProjectScopedChatHref(projectId, conversationId));
                  setSearchOpen(false);
                }}
              />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden p-2">
        <div className="mb-2" hidden={!showExpandedChrome}>
          <button
            type="button"
            className="hover:bg-sidebar-accent/80 flex w-full items-center justify-between rounded-md px-2 py-2 text-left text-sm"
            aria-label={tNav("projects")}
            aria-expanded={!projectsCollapsed}
            aria-controls="sidebar-projects-tree"
            onClick={toggleProjectsCollapsed}
          >
            <span className="font-medium">{tNav("projects")}</span>
            <span className="text-muted-foreground text-xs" aria-hidden="true">
              {projects.length}
            </span>
          </button>

          <div id="sidebar-projects-tree" hidden={projectsCollapsed}>
            {projectsLoading ? <div className="text-muted-foreground px-2 py-1 text-xs">Loading…</div> : null}
            {projectsError ? (
              <div className="text-destructive px-2 py-1 text-xs">Failed to load projects</div>
            ) : null}

            {!projectsLoading && !projectsError
              ? projects.map((p) => (
                  <SidebarProjectNode
                    key={p.id}
                    project={p}
                    expanded={expandedSet.has(p.id)}
                    toggleExpanded={() => toggleProjectExpanded(p.id)}
                    activeProjectId={activeProject?.id ?? null}
                    activateProject={activate}
                    openProjectInChat={openProjectInChat}
                    selectedConversationId={selectedConversationId}
                    chatRouteActive={Boolean(pathname?.includes("/chat"))}
                    searchQuery={searchQuery}
                    onConversationDeleted={handleConversationDeleted}
                    onSelectConversation={(projectId, conversationId) => {
                      router.push(buildProjectScopedChatHref(projectId, conversationId));
                    }}
                  />
                ))
              : null}

            {projectsTotal > 64 ? (
              <Link href="/projects" className="text-muted-foreground mt-2 block px-2 text-xs underline">
                View all projects
              </Link>
            ) : null}
          </div>
        </div>

        <nav className="flex flex-col gap-0.5 pt-2" aria-label="Main">
          {primaryLinks
            .filter((l) => (l.key === "admin" ? canSeeAdmin : true))
            .map((item) => {
              const documentsHref =
                item.key === "documents" && activeProject?.id
                  ? buildProjectScopedDocumentsHref(activeProject.id)
                  : item.href;
              const href = item.key === "documents" ? documentsHref : item.href;
              const active =
                item.key === "documents"
                  ? pathname === "/documents" || (pathname ?? "").startsWith("/documents/")
                  : pathname === item.href || (pathname ?? "").startsWith(`${item.href}/`);
              const Icon = item.icon;
              const railOnly = railCollapsed && variant === "desktop";
              return (
                <Link
                  key={item.key}
                  href={href}
                  title={railOnly ? tNav(item.key) : undefined}
                  className={cn(
                    "flex items-center gap-2 rounded-md py-2 text-sm transition-colors",
                    railOnly ? "justify-center px-2" : "px-2",
                    active
                      ? "bg-sidebar-accent text-sidebar-accent-foreground"
                      : "hover:bg-sidebar-accent/80",
                  )}
                  onClick={() => onNavigate?.()}
                >
                  <Icon className="size-4 shrink-0" aria-hidden />
                  <span className={cn("truncate", railOnly && "sr-only")}>{tNav(item.key)}</span>
                </Link>
              );
            })}
        </nav>
      </div>

      <div className="mt-auto flex shrink-0 flex-col gap-1 border-border border-t p-2">
        <Link
          href="/settings"
          title={
            railCollapsed && variant === "desktop" ? tNav("settingsPage") : undefined
          }
          className={cn(
            "flex items-center gap-2 rounded-md py-2 text-sm transition-colors",
            railCollapsed && variant === "desktop" ? "justify-center px-2" : "px-2",
            pathname === "/settings" || pathname.startsWith("/settings/")
              ? "bg-sidebar-accent text-sidebar-accent-foreground"
              : "hover:bg-sidebar-accent/80",
          )}
          onClick={() => onNavigate?.()}
        >
          <Settings className="size-4 shrink-0" aria-hidden />
          <span className={cn("truncate", railCollapsed && variant === "desktop" && "sr-only")}>
            {tNav("settingsPage")}
          </span>
        </Link>
        {onSignOut ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            title={railCollapsed && variant === "desktop" ? tNav("signOut") : undefined}
            className={cn(
              "text-sidebar-foreground hover:bg-sidebar-accent/80 h-auto justify-start gap-2 py-2 font-normal",
              railCollapsed && variant === "desktop" ? "justify-center px-2" : "px-2",
            )}
            onClick={() => {
              if (variant === "drawer") {
                onNavigate?.();
              }
              onSignOut();
            }}
          >
            <LogOut className="size-4 shrink-0" aria-hidden />
            <span className={cn("truncate", railCollapsed && variant === "desktop" && "sr-only")}>
              {tNav("signOut")}
            </span>
          </Button>
        ) : null}
      </div>
    </aside>
  );
}

type SidebarProjectNodeProps = Readonly<{
  project: ProjectSummary;
  expanded: boolean;
  toggleExpanded: () => void;
  activeProjectId: string | null;
  activateProject: (p: ProjectSummary) => Promise<void>;
  openProjectInChat: (p: ProjectSummary) => Promise<void>;
  selectedConversationId: string | null;
  chatRouteActive: boolean;
  searchQuery: string;
  onConversationDeleted: (projectId: string, conversationId: string) => void;
  onSelectConversation: (projectId: string, conversationId: string) => void;
}>;

function SidebarProjectNode({
  project,
  expanded,
  toggleExpanded,
  activeProjectId,
  activateProject,
  openProjectInChat,
  selectedConversationId,
  chatRouteActive,
  searchQuery,
  onConversationDeleted,
  onSelectConversation,
}: SidebarProjectNodeProps) {
  // Avoid fan-out: only load conversations when expanded or active.
  // Cross-project search loads conversations progressively via the search UI.
  const shouldLoadConversations = expanded || activeProjectId === project.id;
  const convsQ = useConversations(shouldLoadConversations ? project.id : undefined);
  const convs = convsQ.data ?? [];

  const filtered = searchQuery
    ? convs.filter((c) => c.title.toLowerCase().includes(searchQuery))
    : convs;

  const tChat = useTranslations("Chat");
  const [pendingDelete, setPendingDelete] = useState<{ id: string; title: string } | null>(null);

  return (
    <div className="mt-1">
      <div className="flex items-center gap-1">
        <button
          type="button"
          className={cn(
            "hover:bg-sidebar-accent/80 flex flex-1 items-center gap-2 rounded-md px-2 py-1.5 text-sm",
            activeProjectId === project.id && "bg-sidebar-accent/60 ring-1 ring-sidebar-accent",
          )}
          onClick={() => void openProjectInChat(project)}
        >
          <ProjectVisual iconKey={project.iconKey} colorHex={project.colorHex} />
          <span className="truncate">{project.name}</span>
        </button>
        <button
          type="button"
          className="hover:bg-sidebar-accent/80 rounded-md px-2 py-1 text-xs"
          aria-expanded={expanded}
          aria-controls={`sidebar-project-${project.id}-chats`}
          aria-label={expanded ? `Collapse chats for ${project.name}` : `Expand chats for ${project.name}`}
          onClick={toggleExpanded}
        >
          {expanded ? "–" : "+"}
        </button>
      </div>

      <div id={`sidebar-project-${project.id}-chats`} hidden={!expanded}>
        {convsQ.isLoading ? (
          <div className="text-muted-foreground px-6 py-1 text-xs">Loading chats…</div>
        ) : null}
        {convsQ.isError ? <div className="text-destructive px-6 py-1 text-xs">Failed to load chats</div> : null}
        {!convsQ.isLoading && !convsQ.isError
          ? filtered.map((c) => (
              <div key={c.id} className="ml-4 flex w-[calc(100%-1rem)] items-center gap-1">
                <button
                  type="button"
                  className={cn(
                    "hover:bg-sidebar-accent/80 min-w-0 flex-1 rounded-md px-2 py-1 text-left text-xs",
                    chatRouteActive &&
                      selectedConversationId === c.id &&
                      "bg-sidebar-accent text-sidebar-accent-foreground",
                  )}
                  onClick={async () => {
                    if (activeProjectId !== project.id) {
                      await activateProject(project);
                    }
                    onSelectConversation(project.id, c.id);
                  }}
                >
                  <span className="line-clamp-1">{c.title}</span>
                </button>
                <button
                  type="button"
                  className="hover:bg-sidebar-accent/80 text-muted-foreground hover:text-destructive shrink-0 rounded-md p-1"
                  aria-label={tChat("deleteConversationTriggerAria", {
                    title: (c.title ?? "").trim() || tChat("deleteConversationUntitled"),
                  })}
                  onClick={(e) => {
                    e.stopPropagation();
                    setPendingDelete({ id: c.id, title: c.title ?? "" });
                  }}
                >
                  <Trash2 className="size-3.5" aria-hidden />
                </button>
              </div>
            ))
          : null}
      </div>
      <DeleteConversationDialog
        open={Boolean(pendingDelete)}
        onOpenChange={(next) => {
          if (!next) setPendingDelete(null);
        }}
        projectId={project.id}
        conversationId={pendingDelete?.id}
        conversationTitle={pendingDelete?.title ?? ""}
        onDeleted={() => {
          const id = pendingDelete?.id;
          if (id) onConversationDeleted(project.id, id);
        }}
      />
    </div>
  );
}

type SearchChatsBodyProps = Readonly<{
  projects: ProjectSummary[];
  activeProjectId: string | null;
  expandedProjectIds: string[];
  query: string;
  activateProject: (p: ProjectSummary) => Promise<void>;
  onConversationDeleted: (projectId: string, conversationId: string) => void;
  onPickConversation: (projectId: string, conversationId: string) => void;
}>;

function SearchChatsBody({
  projects,
  activeProjectId,
  expandedProjectIds,
  query,
  activateProject,
  onConversationDeleted,
  onPickConversation,
}: SearchChatsBodyProps) {
  const q = query.trim().toLowerCase();
  const prioritizedProjectIds = useMemo(() => {
    const ids: string[] = [];
    if (activeProjectId) ids.push(activeProjectId);
    for (const id of expandedProjectIds) {
      if (!ids.includes(id)) ids.push(id);
    }
    // Progressive loading: append remaining projects after the prioritized ones.
    for (const p of projects) {
      if (!ids.includes(p.id)) ids.push(p.id);
    }
    return ids;
  }, [activeProjectId, expandedProjectIds, projects]);

  const [loadedCount, setLoadedCount] = useState(() => Math.min(2, prioritizedProjectIds.length));

  // Simple progressive loader: incrementally include more projects while a query exists.
  // This avoids loading every project conversation list at once.
  useEffect(() => {
    if (!q) {
      const t = setTimeout(() => setLoadedCount(Math.min(2, prioritizedProjectIds.length)), 0);
      return () => clearTimeout(t);
    }
    if (loadedCount < prioritizedProjectIds.length) {
      const t = setTimeout(() => setLoadedCount((c) => Math.min(c + 1, prioritizedProjectIds.length)), 150);
      return () => clearTimeout(t);
    }
  }, [q, loadedCount, prioritizedProjectIds.length]);

  if (!q) {
    return <p className="text-muted-foreground text-sm">Type to search chat titles (grouped by project).</p>;
  }

  const loadedProjectIds = prioritizedProjectIds.slice(0, loadedCount);
  const remaining = prioritizedProjectIds.length - loadedCount;

  return (
    <div className="flex flex-col gap-3">
      {remaining > 0 ? (
        <p className="text-muted-foreground text-xs">Loading more projects… ({remaining} remaining)</p>
      ) : null}
      <div className="max-h-[50vh] overflow-y-auto rounded-md border p-2">
        <div className="flex flex-col gap-2">
          {loadedProjectIds.map((pid) => {
            const project = projects.find((p) => p.id === pid);
            if (!project) return null;
            return (
              <SearchProjectGroup
                key={pid}
                project={project}
                query={q}
                activeProjectId={activeProjectId}
                activateProject={activateProject}
                onConversationDeleted={onConversationDeleted}
                onPickConversation={onPickConversation}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

type SearchProjectGroupProps = Readonly<{
  project: ProjectSummary;
  query: string;
  activeProjectId: string | null;
  activateProject: (p: ProjectSummary) => Promise<void>;
  onConversationDeleted: (projectId: string, conversationId: string) => void;
  onPickConversation: (projectId: string, conversationId: string) => void;
}>;

function SearchProjectGroup({
  project,
  query,
  activeProjectId,
  activateProject,
  onConversationDeleted,
  onPickConversation,
}: SearchProjectGroupProps) {
  const tChat = useTranslations("Chat");
  const [pendingDelete, setPendingDelete] = useState<{ id: string; title: string } | null>(null);
  const convsQ = useConversations(project.id);
  const convs = convsQ.data ?? [];
  const matches = convs.filter((c) => c.title.toLowerCase().includes(query));
  if (matches.length === 0 && !convsQ.isLoading) return null;

  return (
    <div className="flex flex-col gap-1">
      <div className="text-muted-foreground flex items-center justify-between text-xs">
        <span className="truncate">{project.name}</span>
        <span>{matches.length}</span>
      </div>
      {convsQ.isLoading ? <p className="text-muted-foreground text-xs">Loading chats…</p> : null}
      {matches.map((c) => (
        <div key={c.id} className="flex items-center gap-1">
          <button
            type="button"
            className="hover:bg-muted flex min-w-0 flex-1 items-center justify-between rounded-md px-2 py-1 text-left text-sm"
            onClick={async () => {
              if (activeProjectId !== project.id) {
                await activateProject(project);
              }
              onPickConversation(project.id, c.id);
            }}
          >
            <span className="truncate">{c.title}</span>
            <span className="text-muted-foreground shrink-0 text-xs">Open</span>
          </button>
          <button
            type="button"
            className="text-muted-foreground hover:text-destructive shrink-0 rounded-md p-1"
            aria-label={tChat("deleteConversationTriggerAria", {
              title: (c.title ?? "").trim() || tChat("deleteConversationUntitled"),
            })}
            onClick={(e) => {
              e.stopPropagation();
              setPendingDelete({ id: c.id, title: c.title ?? "" });
            }}
          >
            <Trash2 className="size-4" aria-hidden />
          </button>
        </div>
      ))}
      <DeleteConversationDialog
        open={Boolean(pendingDelete)}
        onOpenChange={(next) => {
          if (!next) setPendingDelete(null);
        }}
        projectId={project.id}
        conversationId={pendingDelete?.id}
        conversationTitle={pendingDelete?.title ?? ""}
        onDeleted={() => {
          const id = pendingDelete?.id;
          if (id) onConversationDeleted(project.id, id);
        }}
      />
    </div>
  );
}
