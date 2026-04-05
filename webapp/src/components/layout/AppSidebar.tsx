"use client";

import { FileText, FlaskConical, FolderKanban, MessageSquare, Settings, Shield } from "lucide-react";
import { useTranslations } from "next-intl";
import { usePathname } from "@/navigation";
import { Link } from "@/navigation";
import { cn } from "@/lib/utils";

const nav = [
  { href: "/projects" as const, key: "projects" as const, icon: FolderKanban },
  { href: "/documents" as const, key: "documents" as const, icon: FileText },
  { href: "/chat" as const, key: "chat" as const, icon: MessageSquare },
  { href: "/lab" as const, key: "lab" as const, icon: FlaskConical },
  { href: "/admin" as const, key: "admin" as const, icon: Shield },
  { href: "/settings" as const, key: "settingsPage" as const, icon: Settings },
];

export function AppSidebar() {
  const t = useTranslations("Nav");
  const pathname = usePathname();

  return (
    <aside className="flex w-[220px] shrink-0 flex-col border-border border-r bg-sidebar text-sidebar-foreground">
      <div className="flex h-14 items-center border-border border-b px-3">
        <span className="font-semibold text-sm tracking-tight">RAG</span>
      </div>
      <nav className="flex flex-1 flex-col gap-0.5 p-2" aria-label="Main">
        {nav.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors",
                active
                  ? "bg-sidebar-accent text-sidebar-accent-foreground"
                  : "hover:bg-sidebar-accent/80",
              )}
            >
              <Icon className="size-4 shrink-0" aria-hidden />
              <span className="truncate">{t(item.key)}</span>
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
