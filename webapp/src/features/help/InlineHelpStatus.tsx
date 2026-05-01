"use client";

import type { TraceStatus } from "@/features/trace/trace-types";
import { cn } from "@/lib/utils";

export type InlineHelpStatusProps = Readonly<{
  status: TraceStatus;
  label: string;
  className?: string;
}>;

const variantClass: Record<TraceStatus, string> = {
  info: "text-muted-foreground border-border bg-muted/30",
  in_progress: "border-primary/30 bg-primary/5 text-foreground",
  success: "border-emerald-500/30 bg-emerald-500/10 text-emerald-800 dark:text-emerald-200",
  warning: "border-amber-500/40 bg-amber-500/10 text-amber-950 dark:text-amber-100",
  error: "border-destructive/40 bg-destructive/10 text-destructive",
};

/**
 * Generic inline status chip for Phase 3B; chat/lab-specific wiring stays in later batches.
 */
export function InlineHelpStatus({ status, label, className }: InlineHelpStatusProps) {
  return (
    <p
      role="status"
      aria-live={status === "in_progress" ? "polite" : undefined}
      className={cn(
        "inline-flex max-w-full items-center rounded-md border px-2 py-1 text-xs font-medium",
        variantClass[status],
        status === "in_progress" && "animate-pulse",
        className,
      )}
    >
      <span className="truncate">{label}</span>
    </p>
  );
}
