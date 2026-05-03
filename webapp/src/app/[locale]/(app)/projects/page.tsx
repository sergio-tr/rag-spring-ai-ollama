"use client";

import { NewProjectDialog } from "@/features/projects/components/NewProjectDialog";
import { ProjectGrid } from "@/features/projects/components/ProjectGrid";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { useSyncActiveProjectWithList } from "@/features/projects/hooks/use-sync-active-project";
import { useTranslations } from "next-intl";
import { useAppStore } from "@/store/app.store";

export default function ProjectsPage() {
  const t = useTranslations("Projects");
  const activeProject = useAppStore((s) => s.activeProject);
  const { data, isLoading, isError } = useProjectList(0, 24);
  const items = data?.items ?? [];
  // Pass undefined until the first list payload exists — `[]` from `data?.items ?? []` when `data` is
  // still undefined would clear activeProject (create flow: invalidate after POST+activate).
  useSyncActiveProjectWithList(data === undefined ? undefined : items);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
          <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
        </div>
        <NewProjectDialog />
      </div>
      {isLoading && (
        <p className="text-muted-foreground text-sm" aria-live="polite">
          {t("loading")}
        </p>
      )}
      {isError && (
        <p className="text-destructive text-sm" role="alert">
          {t("loadError")}
        </p>
      )}
      {!isLoading && !isError && items.length === 0 && (
        <p className="text-muted-foreground text-sm">{t("empty")}</p>
      )}
      {!isLoading && !isError && items.length > 0 && !activeProject ? (
        <output className="text-muted-foreground block text-sm">{t("noActiveSelectionHint")}</output>
      ) : null}
      {!isLoading && !isError && items.length > 0 && (
        <ProjectGrid items={items} />
      )}
    </div>
  );
}
