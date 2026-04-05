"use client";

import { AppSubnavSectionLayout } from "@/components/layout/app-subnav-section-layout";
import type { ReactNode } from "react";
import { useTranslations } from "next-intl";

const tabDefs = [
  { href: "/settings", labelKey: "tabGeneral" as const },
  { href: "/settings/user", labelKey: "tabUser" as const },
  { href: "/settings/project", labelKey: "tabProject" as const },
  { href: "/settings/presets", labelKey: "tabPresets" as const },
  { href: "/settings/data", labelKey: "tabData" as const },
  { href: "/settings/account", labelKey: "tabAccount" as const },
];

export default function SettingsLayout({ children }: Readonly<{ children: ReactNode }>) {
  const t = useTranslations("Settings");
  const tabs = tabDefs.map((d) => ({ href: d.href, label: t(d.labelKey) }));

  return (
    <AppSubnavSectionLayout
      title={t("title")}
      subtitle={t("subtitle")}
      navAriaLabel={t("sectionsNavLabel")}
      sectionRootHref="/settings"
      tabs={tabs}
    >
      {children}
    </AppSubnavSectionLayout>
  );
}
