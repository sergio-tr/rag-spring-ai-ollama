"use client";

import { Link, usePathname } from "@/navigation";
import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

export type AppSubnavTab = {
  href: string;
  label: string;
};

type AppSubnavSectionLayoutProps = {
  title: string;
  subtitle: string;
  /** Optional extra line under the subtitle (e.g. ADR note). */
  noteBelowSubtitle?: string;
  navAriaLabel: string;
  tabs: AppSubnavTab[];
  /** When the tab href is a section root, match pathname exactly only for that href. */
  sectionRootHref?: string;
  children: ReactNode;
};

/**
 * Shared heading + horizontal sub-navigation used by Lab and Settings shells.
 */
export function AppSubnavSectionLayout({
  title,
  subtitle,
  noteBelowSubtitle,
  navAriaLabel,
  tabs,
  sectionRootHref,
  children,
}: AppSubnavSectionLayoutProps) {
  const pathname = usePathname();
  const root = sectionRootHref ?? tabs[0]?.href ?? "";

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{title}</h1>
        <p className="text-muted-foreground text-sm">{subtitle}</p>
        {noteBelowSubtitle ? (
          <p className="text-muted-foreground mt-2 max-w-3xl text-xs">{noteBelowSubtitle}</p>
        ) : null}
      </div>
      <nav
        className="flex flex-wrap gap-1 border-border border-b pb-2"
        aria-label={navAriaLabel}
      >
        {tabs.map((tab) => {
          const active =
            tab.href === root ? pathname === tab.href : pathname === tab.href || pathname.startsWith(`${tab.href}/`);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                active ? "bg-muted text-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
