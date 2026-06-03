"use client";

import { AppSubnavSectionLayout } from "@/components/layout/app-subnav-section-layout";
import { LabActiveJobsBanner } from "@/features/lab/components/lab-active-jobs-banner";
import { LabBackgroundJobBanner } from "@/features/lab/components/lab-background-job-banner";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

const tabDefs = [
  { href: "/lab", labelKey: "labOverview" as const },
  { href: "/lab/evaluation/llm", labelKey: "labLlmEval" as const },
  { href: "/lab/evaluation/embedding", labelKey: "labEmbeddingEval" as const },
  { href: "/lab/evaluation/rag", labelKey: "labRagEval" as const },
  { href: "/lab/classifier", labelKey: "labClassifier" as const },
];

export default function LabLayout({ children }: Readonly<{ children: ReactNode }>) {
  const t = useTranslations("Lab");
  const tabs = tabDefs.map((d) => ({ href: d.href, label: t(d.labelKey) }));

  return (
    <AppSubnavSectionLayout
      title={t("title")}
      subtitle={t("compactLayoutSubtitle")}
      navAriaLabel={t("sectionsNavLabel")}
      sectionRootHref="/lab"
      tabs={tabs}
    >
      <div className="space-y-4">
        <LabActiveJobsBanner />
        <LabBackgroundJobBanner />
        {children}
      </div>
    </AppSubnavSectionLayout>
  );
}
