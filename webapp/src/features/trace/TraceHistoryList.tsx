"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { useTraceStore } from "@/features/trace/trace.store";
import type { TraceEvent } from "@/features/trace/trace-types";

function formatTime(iso: string): string {
  try {
    const d = new Date(iso);
    return new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      month: "short",
      day: "numeric",
    }).format(d);
  } catch {
    return iso;
  }
}

function statusBadgeVariant(
  status: TraceEvent["status"],
): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "success":
      return "default";
    case "error":
      return "destructive";
    case "warning":
      return "outline";
    default:
      return "secondary";
  }
}

/**
 * Read-only list of trace events for activity UI. Metadata values are intentionally omitted from display.
 */
export function TraceHistoryList() {
  const t = useTranslations("Help.trace");
  const events = useTraceStore((s) => s.events);
  const rows = useMemo(() => [...events].reverse(), [events]);

  if (rows.length === 0) {
    return <p className="text-muted-foreground text-sm">{t("empty")}</p>;
  }

  return (
    <ul className="flex flex-col gap-3 text-sm" data-testid="trace-history-list">
      {rows.map((ev) => (
        <li key={ev.id} className="rounded-md border border-border/80 bg-muted/20 px-2 py-2">
          <div className="flex flex-wrap items-center gap-2">
            <time className="text-muted-foreground text-xs tabular-nums" dateTime={ev.timestamp}>
              {formatTime(ev.timestamp)}
            </time>
            <Badge variant="outline" className="font-normal capitalize">
              {ev.section}
            </Badge>
            <Badge variant={statusBadgeVariant(ev.status)} className="font-normal">
              {ev.status.replace(/_/g, " ")}
            </Badge>
          </div>
          <p className="mt-1 font-medium text-xs text-muted-foreground">{ev.action}</p>
          <p className="text-foreground text-xs leading-snug">{ev.message}</p>
        </li>
      ))}
    </ul>
  );
}
