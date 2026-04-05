"use client";

import { NewProjectDialog } from "@/features/projects/components/NewProjectDialog";
import { ProjectGrid } from "@/features/projects/components/ProjectGrid";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { useSyncActiveProjectWithList } from "@/features/projects/hooks/use-sync-active-project";
import { useTranslations } from "next-intl";

export default function ProjectsPage() {
  const t = useTranslations("Projects");
  const { data, isLoading, isError } = useProjectList(0, 24);
  useSyncActiveProjectWithList(data?.items);

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
      {!isLoading && !isError && data && data.items.length === 0 && (
        <p className="text-muted-foreground text-sm">{t("empty")}</p>
      )}
      {!isLoading && !isError && data && data.items.length > 0 && (
        <ProjectGrid items={data.items} />
      )}
    </div>
  );
}
