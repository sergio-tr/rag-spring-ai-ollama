"use client";

import { useTranslations } from "next-intl";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";

type ProjectIndexProfileSectionProps = Readonly<{
  projectId: string;
}>;

export function ProjectIndexProfileSection({ projectId }: ProjectIndexProfileSectionProps) {
  const t = useTranslations("Settings");
  const profileQuery = useProjectIndexProfile(projectId);
  const userEmbeddingDefaults = useMeEffectiveEmbeddingDefaults();
  const profile = profileQuery.data;

  if (profileQuery.isLoading) {
    return (
      <p className="text-muted-foreground text-sm" data-testid="project-index-profile-loading">
        {t("configLoading")}
      </p>
    );
  }

  if (profileQuery.isError || !profile) {
    return (
      <p className="text-muted-foreground text-sm" data-testid="project-index-profile-empty">
        {t("projectIndexProfileEmpty")}
      </p>
    );
  }

  const userDefaultEmbedding = userEmbeddingDefaults.data?.embeddingModel?.trim() ?? "";
  const activeEmbedding = profile.embeddingModelId?.trim() ?? "";
  const drift =
    Boolean(userDefaultEmbedding && activeEmbedding) && userDefaultEmbedding !== activeEmbedding;

  return (
    <section
      className="min-w-0 max-w-full overflow-hidden rounded-md border bg-muted/20 p-3 text-sm"
      data-testid="project-index-profile-section"
    >
      <h3 className="font-medium">{t("projectIndexProfileTitle")}</h3>
      <p className="text-muted-foreground mt-1 break-words text-xs">{t("projectIndexProfileDescription")}</p>
      {profile.materializationStrategy?.trim().toUpperCase() === "STRUCTURED_SEARCH" ? (
        <p
          className="mt-2 break-words rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100"
          data-testid="project-index-profile-structured-search-warning"
          role="status"
        >
          {t("structuredSearchLegacyProjectWarning")}
        </p>
      ) : null}
      {drift ? (
        <p
          className="mt-2 break-words rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100"
          data-testid="project-index-profile-drift-badge"
          role="status"
        >
          {t("projectIndexProfileDriftWarning", {
            userDefault: userDefaultEmbedding,
            active: activeEmbedding,
          })}
        </p>
      ) : null}
      <dl className="mt-3 flex flex-wrap gap-2 text-xs">
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileEmbeddingModel")}</dt>
          <dd className="break-all font-mono">{activeEmbedding || "-"}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileMaterialization")}</dt>
          <dd className="break-all font-mono">{profile.materializationStrategy ?? "-"}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileChunkMaxChars")}</dt>
          <dd className="font-mono">{profile.chunkMaxChars}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileChunkOverlap")}</dt>
          <dd className="font-mono">{profile.chunkOverlap ?? "-"}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileMetadataEnabled")}</dt>
          <dd className="font-mono" data-testid="project-index-profile-metadata-capability">
            {profile.metadataEnabled ? t("projectIndexProfileMetadataCapabilityYes") : t("projectIndexProfileMetadataCapabilityNo")}
          </dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("projectIndexProfileHash")}</dt>
          <dd className="break-all font-mono">{profile.profileHash}</dd>
        </div>
      </dl>
      <p className="text-muted-foreground mt-3 break-words text-xs" data-testid="project-index-profile-fixed-hint">
        {t("projectIndexProfileFixedIndexHint")}
      </p>
    </section>
  );
}
