"use client";

import { Link, usePathname } from "@/navigation";
import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

const tabs = [
  { href: "/lab", labelKey: "labOverview" as const },
  { href: "/lab/evaluation/llm", labelKey: "labLlmEval" as const },
  { href: "/lab/evaluation/rag", labelKey: "labRagEval" as const },
  { href: "/lab/classifier", labelKey: "labClassifier" as const },
];

export default function LabLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const t = useTranslations("Lab");

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
        <p className="text-muted-foreground mt-2 max-w-3xl text-xs">{t("layoutAdrNote")}</p>
      </div>
      <nav
        className="flex flex-wrap gap-1 border-border border-b pb-2"
        aria-label={t("sectionsNavLabel")}
      >
        {tabs.map((tab) => {
          const active =
            tab.href === "/lab"
              ? pathname === "/lab"
              : pathname === tab.href || pathname.startsWith(`${tab.href}/`);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                active
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {t(tab.labelKey)}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
