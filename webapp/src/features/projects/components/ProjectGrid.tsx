"use client";

import { DeleteConversationDialog } from "@/features/chat/components/DeleteConversationDialog";
import { useConversations, useCreateConversation } from "@/features/chat/hooks/use-conversations";
import { useProjectDocuments } from "@/features/documents/hooks/use-project-documents";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useRouter } from "@/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { DeleteProjectDialog } from "@/features/projects/components/DeleteProjectDialog";
import { EditProjectDialog } from "@/features/projects/components/EditProjectDialog";
import { fetchLatestConversationId } from "@/features/projects/lib/open-project-in-chat";
import {
  buildProjectScopedChatHref,
  buildProjectScopedDocumentsHref,
} from "@/features/projects/lib/open-project-navigation";
import { useActivateProject } from "@/features/projects/hooks/use-projects";
import { ProjectVisual } from "@/features/projects/components/ProjectVisual";
import { activeProjectFromSummary, useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";
import { Trash2 } from "lucide-react";

type ProjectGridProps = {
  items: ProjectSummary[];
};

function ProjectLatestDocumentTeaser({
  projectId,
  docCount,
}: Readonly<{ projectId: string; docCount: number }>) {
  const t = useTranslations("Projects");
  const enabled = docCount > 0;
  const { data, isLoading } = useProjectDocuments(enabled ? projectId : undefined);
  const latest = useMemo(() => {
    if (!data?.length) return null;
    return [...data].sort(
      (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime(),
    )[0];
  }, [data]);

  if (docCount <= 0) {
    return <p className="text-muted-foreground text-xs">{t("cardNoDocumentsYet")}</p>;
  }
  if (isLoading) {
    return <p className="text-muted-foreground text-xs">{t("cardDocumentsLoading")}</p>;
  }
  if (!latest) {
    return <p className="text-muted-foreground text-xs">{t("cardDocumentsSummaryFallback")}</p>;
  }
  return (
    <p className="text-muted-foreground text-xs">
      <span className="font-medium text-foreground">{t("cardLatestDocument")}:</span>{" "}
      <span className="truncate" title={latest.fileName}>
        {latest.fileName}
      </span>
    </p>
  );
}

function ProjectChatsSection({ project }: Readonly<{ project: ProjectSummary }>) {
  const t = useTranslations("Projects");
  const tChat = useTranslations("Chat");
  const router = useRouter();
  const activate = useActivateProject();
  const createConv = useCreateConversation(project.id);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; title: string } | null>(null);

  const listEnabled = project.convCount > 0;
  const { data, isLoading, isError } = useConversations(listEnabled ? project.id : undefined);

  const sorted = useMemo(() => {
    if (!data?.length) return [];
    return [...data].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    );
  }, [data]);

  async function openScopedChat(conversationId: string | null) {
    await activate.mutateAsync(activeProjectFromSummary(project));
    router.push(buildProjectScopedChatHref(project.id, conversationId));
  }

  if (project.convCount <= 0) {
    return (
      <div className="flex flex-col gap-2 border-border/60 border-t pt-2">
        <p className="text-muted-foreground text-xs">{t("cardNoChatsYet")}</p>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="w-fit"
          disabled={createConv.isPending || activate.isPending}
          onClick={() =>
            void (async () => {
              await activate.mutateAsync(activeProjectFromSummary(project));
              const created = await createConv.mutateAsync();
              router.push(buildProjectScopedChatHref(project.id, created.id));
            })()
          }
        >
          {t("startFirstChat")}
        </Button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="border-border/60 border-t pt-2">
        <p className="text-muted-foreground text-xs">{t("cardChatsLoading")}</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="border-border/60 border-t pt-2">
        <p className="text-destructive text-xs" role="alert">
          {t("cardChatsLoadError")}
        </p>
      </div>
    );
  }

  if (sorted.length === 0) {
    return (
      <div className="border-border/60 border-t pt-2">
        <p className="text-muted-foreground text-xs">{t("cardChatsStaleHint")}</p>
      </div>
    );
  }

  const latest = sorted[0];
  const rest = sorted.slice(1, 3);
  const chatTitle = (c: { title?: string | null }) => c.title?.trim() || t("cardUntitledChat");
  const moreCount = sorted.length > 3 ? sorted.length - 3 : 0;

  return (
    <div className="flex flex-col gap-2 border-border/60 border-t pt-2">
      <div className="text-muted-foreground text-xs">
        <span className="font-medium text-foreground">{t("cardLatestChat")}:</span>{" "}
        <span className="inline-flex max-w-full items-start gap-1">
          <button
            type="button"
            className="min-w-0 max-w-full truncate text-left underline-offset-2 hover:underline"
            onClick={() => void openScopedChat(latest.id)}
          >
            {chatTitle(latest)}
          </button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="size-7 shrink-0 text-muted-foreground hover:text-destructive"
            aria-label={tChat("deleteConversationTriggerAria", { title: chatTitle(latest) })}
            onClick={() => setPendingDelete({ id: latest.id, title: chatTitle(latest) })}
          >
            <Trash2 className="size-3.5" aria-hidden />
          </Button>
        </span>
      </div>
      {rest.length > 0 ? (
        <ul className="flex flex-col gap-1">
          {rest.map((c) => (
            <li key={c.id} className="flex items-center gap-1">
              <button
                type="button"
                className="min-w-0 max-w-full flex-1 truncate text-left text-muted-foreground text-xs underline-offset-2 hover:text-foreground hover:underline"
                onClick={() => void openScopedChat(c.id)}
              >
                {chatTitle(c)}
              </button>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="size-7 shrink-0 text-muted-foreground hover:text-destructive"
                aria-label={tChat("deleteConversationTriggerAria", { title: chatTitle(c) })}
                onClick={() => setPendingDelete({ id: c.id, title: chatTitle(c) })}
              >
                <Trash2 className="size-3.5" aria-hidden />
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      {moreCount > 0 ? (
        <p className="text-muted-foreground text-[11px]">{t("cardMoreChats", { count: moreCount })}</p>
      ) : null}
      <DeleteConversationDialog
        open={Boolean(pendingDelete)}
        onOpenChange={(next) => {
          if (!next) setPendingDelete(null);
        }}
        projectId={project.id}
        conversationId={pendingDelete?.id}
        conversationTitle={pendingDelete?.title ?? ""}
      />
    </div>
  );
}

function BrowseProjectChatsButton({ project }: Readonly<{ project: ProjectSummary }>) {
  const t = useTranslations("Projects");
  const router = useRouter();
  const activate = useActivateProject();
  const [busy, setBusy] = useState(false);

  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      disabled={busy || activate.isPending}
      onClick={async () => {
        setBusy(true);
        try {
          await activate.mutateAsync(activeProjectFromSummary(project));
          router.push(buildProjectScopedChatHref(project.id, null));
        } finally {
          setBusy(false);
        }
      }}
    >
      {t("browseChats")}
    </Button>
  );
}

function BrowseProjectDocumentsButton({ project }: Readonly<{ project: ProjectSummary }>) {
  const t = useTranslations("Projects");
  const router = useRouter();
  const activate = useActivateProject();
  const [busy, setBusy] = useState(false);

  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      disabled={busy || activate.isPending}
      onClick={async () => {
        setBusy(true);
        try {
          await activate.mutateAsync(activeProjectFromSummary(project));
          router.push(buildProjectScopedDocumentsHref(project.id));
        } finally {
          setBusy(false);
        }
      }}
    >
      {t("browseDocuments")}
    </Button>
  );
}

function OpenProjectPrimaryButton({ project }: Readonly<{ project: ProjectSummary }>) {
  const t = useTranslations("Projects");
  const router = useRouter();
  const queryClient = useQueryClient();
  const activate = useActivateProject();
  const [busy, setBusy] = useState(false);

  return (
    <Button
      type="button"
      size="sm"
      variant="default"
      disabled={busy || activate.isPending}
      onClick={async () => {
        setBusy(true);
        try {
          await activate.mutateAsync(activeProjectFromSummary(project));
          const convId = await fetchLatestConversationId(queryClient, project.id);
          router.push(buildProjectScopedChatHref(project.id, convId));
        } finally {
          setBusy(false);
        }
      }}
    >
      {t("openProject")}
    </Button>
  );
}

export function ProjectGrid({ items }: ProjectGridProps) {
  const t = useTranslations("Projects");
  const active = useAppStore((s) => s.activeProject);
  const activate = useActivateProject();

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {items.map((p) => {
        const isActive = active?.id === p.id;
        return (
          <Card key={p.id} className="flex flex-col border-border/80">
            <CardHeader className="gap-1">
              <CardTitle className="flex items-center gap-2 text-lg leading-tight">
                <ProjectVisual
                  iconKey={p.iconKey}
                  colorHex={p.colorHex}
                  dotClassName="inline-block size-3 shrink-0 rounded-full border border-border"
                />
                {p.name}
              </CardTitle>
              {p.description ? (
                <CardDescription className="line-clamp-2">{p.description}</CardDescription>
              ) : null}
            </CardHeader>
            <CardContent className="flex flex-col gap-2 text-muted-foreground text-sm">
              <div className="flex flex-wrap gap-2">
                <Badge variant="secondary">
                  {p.docCount} {t("docs")}
                </Badge>
                <Badge variant="outline">
                  {p.convCount} {t("conversations")}
                </Badge>
              </div>
              <ProjectLatestDocumentTeaser projectId={p.id} docCount={p.docCount} />
              <ProjectChatsSection project={p} />
            </CardContent>
            <CardFooter className="mt-auto flex flex-wrap items-center gap-2">
              <OpenProjectPrimaryButton project={p} />
              <BrowseProjectChatsButton project={p} />
              <BrowseProjectDocumentsButton project={p} />
              <Button
                type="button"
                size="sm"
                variant={isActive ? "secondary" : "outline"}
                disabled={activate.isPending}
                onClick={() => activate.mutate(activeProjectFromSummary(p))}
              >
                {isActive ? t("active") : t("setActiveOnly")}
              </Button>
              <EditProjectDialog project={p} />
              <DeleteProjectDialog project={p} />
            </CardFooter>
          </Card>
        );
      })}
    </div>
  );
}
