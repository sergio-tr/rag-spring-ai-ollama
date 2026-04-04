"use client";

import { useTranslations } from "next-intl";
import styles from "./CollapsiblePanel.module.css";
import { cn } from "@/lib/utils";

type CollapsiblePanelProps = {
  open: boolean;
  children: React.ReactNode;
};

/** Right rail (ChatGPT-style); open/close control lives in AppShell header. */
export function CollapsiblePanel({ open, children }: CollapsiblePanelProps) {
  const t = useTranslations("Panel");

  return (
    <aside
      aria-hidden={!open}
      className={cn(styles.root, open ? styles.open : styles.closed)}
    >
      <div className={styles.inner}>
        <h2 className="mb-3 font-medium text-sm">{t("title")}</h2>
        <p className="text-muted-foreground text-sm leading-relaxed">{children}</p>
      </div>
    </aside>
  );
}
