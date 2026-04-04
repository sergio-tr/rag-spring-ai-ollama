"use client";

import { useTranslations } from "next-intl";
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
import { useActivateProject } from "@/features/projects/hooks/use-projects";
import { useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";

type ProjectGridProps = {
  items: ProjectSummary[];
};

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
                <span
                  className="inline-block size-3 shrink-0 rounded-full border border-border"
                  style={{
                    backgroundColor: p.colorHex && /^#([0-9A-Fa-f]{6})$/.test(p.colorHex) ? p.colorHex : "#9ca3af",
                  }}
                  aria-hidden
                />
                {p.name}
              </CardTitle>
              {p.description ? (
                <CardDescription className="line-clamp-2">{p.description}</CardDescription>
              ) : null}
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2 text-muted-foreground text-sm">
              <Badge variant="secondary">
                {p.docCount} {t("docs")}
              </Badge>
              <Badge variant="outline">
                {p.convCount} {t("conversations")}
              </Badge>
            </CardContent>
            <CardFooter className="mt-auto flex flex-wrap items-center gap-2">
              <Button
                type="button"
                size="sm"
                variant={isActive ? "secondary" : "default"}
                disabled={activate.isPending}
                onClick={() => activate.mutate({ id: p.id, name: p.name })}
              >
                {isActive ? t("active") : t("activate")}
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
