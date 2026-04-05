"use client";

import type { ReactNode } from "react";
import { Link, usePathname } from "@/navigation";
import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

const tabs = [
  { href: "/settings", labelKey: "tabGeneral" as const },
  { href: "/settings/user", labelKey: "tabUser" as const },
  { href: "/settings/project", labelKey: "tabProject" as const },
  { href: "/settings/presets", labelKey: "tabPresets" as const },
  { href: "/settings/data", labelKey: "tabData" as const },
  { href: "/settings/account", labelKey: "tabAccount" as const },
];

export default function SettingsLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const t = useTranslations("Settings");

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
      </div>
      <nav
        className="flex flex-wrap gap-1 border-border border-b pb-2"
        aria-label={t("sectionsNavLabel")}
      >
        {tabs.map((tab) => {
          const active =
            tab.href === "/settings"
              ? pathname === "/settings"
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
