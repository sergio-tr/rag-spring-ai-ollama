"use client";

import {
  Briefcase,
  Code,
  FileText,
  FlaskConical,
  Folder,
  FolderKanban,
  MessageSquare,
  Rocket,
  Search,
  Settings,
  Shield,
  Star,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { usePathname, Link, useRouter } from "@/navigation";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useEffect, useMemo, useState } from "react";
import { useProjectList, useActivateProject } from "@/features/projects/hooks/use-projects";
import { useConversations, useCreateConversation } from "@/features/chat/hooks/use-conversations";
import { useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";
import { NewProjectDialog } from "@/features/projects/components/NewProjectDialog";
import { getStoredUserRole } from "@/lib/user-role";

const primaryLinks = [
  { href: "/projects" as const, key: "projects" as const, icon: FolderKanban },
  { href: "/documents" as const, key: "documents" as const, icon: FileText },
  { href: "/chat" as const, key: "chat" as const, icon: MessageSquare },
  { href: "/lab" as const, key: "lab" as const, icon: FlaskConical },
  { href: "/admin" as const, key: "admin" as const, icon: Shield },
];

const STORAGE_KEY = "rag-sidebar";

function isHexColor(s: string | null | undefined): s is string {
  return Boolean(s && /^#([0-9A-Fa-f]{6})$/.test(s));
}

function getProjectIcon(iconKey: string | null | undefined) {
  switch (iconKey) {
    case "folder":
      return Folder;
    case "briefcase":
      return Briefcase;
    case "star":
      return Star;
    case "code":
      return Code;
    case "rocket":
      return Rocket;
    case "shield":
      return Shield;
    case "chat":
      return MessageSquare;
    case "lab":
      return FlaskConical;
    case "book":
      return FileText;
    default:
      return FolderKanban;
  }
}

type SidebarPersistence = {
  projectsCollapsed: boolean;
  expandedProjectIds: string[];
};

function readSidebarPersistence(): SidebarPersistence {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { projectsCollapsed: false, expandedProjectIds: [] };
    const parsed = JSON.parse(raw) as Partial<SidebarPersistence>;
    return {
      projectsCollapsed: Boolean(parsed.projectsCollapsed),
      expandedProjectIds: Array.isArray(parsed.expandedProjectIds)
        ? parsed.expandedProjectIds.filter((id): id is string => typeof id === "string")
        : [],
    };
  } catch {
    return { projectsCollapsed: false, expandedProjectIds: [] };
  }
}

function writeSidebarPersistence(next: SidebarPersistence) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    /* ignore */
  }
}

export function AppSidebar() {
  const tNav = useTranslations("Nav");
  const tChat = useTranslations("Chat");
  const pathname = usePathname();
  const router = useRouter();

  const canSeeAdmin = getStoredUserRole() === "ADMIN";

  const activeProject = useAppStore((s) => s.activeProject);
  const activateProject = useActivateProject();

  const { data: projectData, isLoading: projectsLoading, isError: projectsError } = useProjectList(0, 64);
  const projects = projectData?.items ?? [];
  const projectsTotal = projectData?.total ?? projects.length;

  const [initialPersisted] = useState<SidebarPersistence>(() => readSidebarPersistence());
  const [projectsCollapsed, setProjectsCollapsed] = useState(initialPersisted.projectsCollapsed);
  const [expandedProjectIds, setExpandedProjectIds] = useState<string[]>(initialPersisted.expandedProjectIds);
  const expandedSet = useMemo(() => new Set(expandedProjectIds), [expandedProjectIds]);

  function writePersisted(next: { projectsCollapsed?: boolean; expandedProjectIds?: string[] }) {
    writeSidebarPersistence({
      projectsCollapsed: next.projectsCollapsed ?? projectsCollapsed,
      expandedProjectIds: next.expandedProjectIds ?? expandedProjectIds,
    });
  }

  function toggleProjectsCollapsed() {
    setProjectsCollapsed((v) => {
      const next = !v;
      writePersisted({ projectsCollapsed: next });
      return next;
    });
  }

  function toggleProjectExpanded(projectId: string) {
    setExpandedProjectIds((prev) => {
      const next = prev.includes(projectId) ? prev.filter((id) => id !== projectId) : [...prev, projectId];
      writePersisted({ expandedProjectIds: next });
      return next;
    });
  }

  async function activate(p: ProjectSummary) {
    await activateProject.mutateAsync({ id: p.id, name: p.name });
  }

  const createConversation = useCreateConversation(activeProject?.id);

  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState("");

  const searchQuery = searchText.trim().toLowerCase();

  return (
    <aside className="flex w-[260px] shrink-0 flex-col border-border border-r bg-sidebar text-sidebar-foreground">
      <div className="flex h-14 items-center justify-between border-border border-b px-3">
        <Link href="/projects" className="flex items-center gap-2">
          <div className="bg-sidebar-accent text-sidebar-accent-foreground flex size-7 items-center justify-center rounded-md text-xs font-semibold">
            RC
          </div>
          <span className="font-semibold text-sm tracking-tight">RAG Console</span>
        </Link>
      </div>

      <div className="border-border border-b p-2">
        <div className="flex flex-col gap-2">
          <NewProjectDialog triggerClassName="w-full" />
          <Button
            type="button"
            size="sm"
            disabled={!activeProject || createConversation.isPending}
            onClick={async () => {
              if (!activeProject?.id) return;
              const c = await createConversation.mutateAsync();
              router.push(`/chat?conversationId=${encodeURIComponent(c.id)}`);
            }}
          >
            {tChat("newConversation")}
          </Button>
          <Dialog
            open={searchOpen}
            onOpenChange={(o) => {
              setSearchOpen(o);
              if (!o) setSearchText("");
            }}
          >
            <DialogTrigger asChild>
              <Button type="button" size="sm" variant="outline">
                <Search className="mr-2 size-4" aria-hidden />
                Search chat
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Search chats</DialogTitle>
              </DialogHeader>
              <Input value={searchText} onChange={(e) => setSearchText(e.target.value)} placeholder="Chat title" />
              <SearchChatsBody
                projects={projects}
                activeProjectId={activeProject?.id ?? null}
                expandedProjectIds={expandedProjectIds}
                query={searchQuery}
                activateProject={activate}
                onPickConversation={(projectId, conversationId) => {
                  if (activeProject?.id !== projectId) {
                    // Activation happens in the picker; this is just a safety net for local state.
                  }
                  router.push(`/chat?conversationId=${encodeURIComponent(conversationId)}`);
                  setSearchOpen(false);
                }}
              />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        <div className="mb-2">
          <button
            type="button"
            className="hover:bg-sidebar-accent/80 flex w-full items-center justify-between rounded-md px-2 py-2 text-left text-sm"
            aria-expanded={!projectsCollapsed}
            aria-controls="sidebar-projects-tree"
            onClick={toggleProjectsCollapsed}
          >
            <span className="font-medium">{tNav("projects")}</span>
            <span className="text-muted-foreground text-xs">{projects.length}</span>
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
                    searchQuery={searchQuery}
                    onSelectConversation={(conversationId) => {
                      router.push(`/chat?conversationId=${encodeURIComponent(conversationId)}`);
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
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors",
                  active
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "hover:bg-sidebar-accent/80",
                )}
              >
                <Icon className="size-4 shrink-0" aria-hidden />
                <span className="truncate">{tNav(item.key)}</span>
              </Link>
            );
          })}
        </nav>
      </div>

      <div className="border-border border-t p-2">
        <Link
          href="/settings"
          className={cn(
            "flex items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors",
            pathname === "/settings" || pathname.startsWith("/settings/")
              ? "bg-sidebar-accent text-sidebar-accent-foreground"
              : "hover:bg-sidebar-accent/80",
          )}
        >
          <Settings className="size-4 shrink-0" aria-hidden />
          <span className="truncate">{tNav("settingsPage")}</span>
        </Link>
      </div>
    </aside>
  );
}

type SidebarProjectNodeProps = {
  project: ProjectSummary;
  expanded: boolean;
  toggleExpanded: () => void;
  activeProjectId: string | null;
  activateProject: (p: ProjectSummary) => Promise<void>;
  searchQuery: string;
  onSelectConversation: (conversationId: string) => void;
};

function SidebarProjectNode({
  project,
  expanded,
  toggleExpanded,
  activeProjectId,
  activateProject,
  searchQuery,
  onSelectConversation,
}: SidebarProjectNodeProps) {
  const Icon = getProjectIcon(project.iconKey);
  // Avoid fan-out: only load conversations when expanded or active.
  // Cross-project search loads conversations progressively via the search UI.
  const shouldLoadConversations = expanded || activeProjectId === project.id;
  const convsQ = useConversations(shouldLoadConversations ? project.id : undefined);
  const convs = convsQ.data ?? [];

  const filtered = searchQuery
    ? convs.filter((c) => c.title.toLowerCase().includes(searchQuery))
    : convs;

  return (
    <div className="mt-1">
      <div className="flex items-center gap-1">
        <button
          type="button"
          className="hover:bg-sidebar-accent/80 flex flex-1 items-center gap-2 rounded-md px-2 py-1.5 text-sm"
          onClick={() => void activateProject(project)}
        >
          <span
            className="inline-block size-2.5 shrink-0 rounded-full border border-border"
            style={{
              backgroundColor: isHexColor(project.colorHex) ? project.colorHex : "#9ca3af",
            }}
            aria-hidden
          />
          <Icon className="size-4 shrink-0" aria-hidden />
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
              <button
                key={c.id}
                type="button"
                className="hover:bg-sidebar-accent/80 ml-4 w-[calc(100%-1rem)] rounded-md px-2 py-1 text-left text-xs"
                onClick={async () => {
                  if (activeProjectId !== project.id) {
                    await activateProject(project);
                  }
                  onSelectConversation(c.id);
                }}
              >
                <span className="line-clamp-1">{c.title}</span>
              </button>
            ))
          : null}
      </div>
    </div>
  );
}

type SearchChatsBodyProps = {
  projects: ProjectSummary[];
  activeProjectId: string | null;
  expandedProjectIds: string[];
  query: string;
  activateProject: (p: ProjectSummary) => Promise<void>;
  onPickConversation: (projectId: string, conversationId: string) => void;
};

function SearchChatsBody({
  projects,
  activeProjectId,
  expandedProjectIds,
  query,
  activateProject,
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
      setLoadedCount(Math.min(2, prioritizedProjectIds.length));
      return;
    }
    if (loadedCount < prioritizedProjectIds.length) {
      const t = setTimeout(() => setLoadedCount((c) => Math.min(c + 1, prioritizedProjectIds.length)), 150);
      return () => clearTimeout(t);
    }
    return;
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
                onPickConversation={onPickConversation}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

type SearchProjectGroupProps = {
  project: ProjectSummary;
  query: string;
  activeProjectId: string | null;
  activateProject: (p: ProjectSummary) => Promise<void>;
  onPickConversation: (projectId: string, conversationId: string) => void;
};

function SearchProjectGroup({
  project,
  query,
  activeProjectId,
  activateProject,
  onPickConversation,
}: SearchProjectGroupProps) {
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
        <button
          key={c.id}
          type="button"
          className="hover:bg-muted flex w-full items-center justify-between rounded-md px-2 py-1 text-left text-sm"
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
      ))}
    </div>
  );
}
