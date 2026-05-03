"use client";

import { AppSubnavSectionLayout } from "@/components/layout/app-subnav-section-layout";
import { LabBackgroundJobBanner } from "@/features/lab/components/lab-background-job-banner";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

const tabDefs = [
  { href: "/lab", labelKey: "labOverview" as const },
  { href: "/lab/evaluation/llm", labelKey: "labLlmEval" as const },
  { href: "/lab/evaluation/rag", labelKey: "labRagEval" as const },
  { href: "/lab/classifier", labelKey: "labClassifier" as const },
];

export default function LabLayout({ children }: { children: ReactNode }) {
  const t = useTranslations("Lab");
  const tabs = tabDefs.map((d) => ({ href: d.href, label: t(d.labelKey) }));

  return (
    <AppSubnavSectionLayout
      title={t("title")}
      subtitle={t("subtitle")}
      noteBelowSubtitle={t("layoutAdrNote")}
      navAriaLabel={t("sectionsNavLabel")}
      sectionRootHref="/lab"
      tabs={tabs}
    >
      <div className="space-y-4">
        <LabBackgroundJobBanner />
        {children}
      </div>
    </AppSubnavSectionLayout>
  );
}
