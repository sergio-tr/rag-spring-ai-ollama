"use client";

import type { RagIndexReadinessDisplay } from "@/features/lab/lib/rag-index-readiness";
import { useTranslations } from "next-intl";

export type RagIndexReadinessMessageProps = {
  display: RagIndexReadinessDisplay | null;
};

const KIND_CLASS: Record<RagIndexReadinessDisplay["kind"], string> = {
  info: "border-sky-500/40 bg-sky-500/10 text-sky-950 dark:text-sky-100",
  success: "border-emerald-500/40 bg-emerald-500/10 text-emerald-950 dark:text-emerald-100",
  warning: "border-amber-500/40 bg-amber-500/10 text-amber-950 dark:text-amber-100",
  blocking: "border-amber-500/40 bg-amber-500/10 text-amber-950 dark:text-amber-100",
};

export function RagIndexReadinessMessage({ display }: RagIndexReadinessMessageProps) {
  const t = useTranslations("Lab");

  if (!display) {
    return null;
  }

  return (
    <output
      data-testid={display.testId}
      data-readiness-kind={display.kind}
      className={`block rounded-md border p-3 text-xs ${KIND_CLASS[display.kind]}`}
    >
      {t(display.messageKey, display.messageParams)}
    </output>
  );
}
