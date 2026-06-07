"use client";

import { Badge } from "@/components/ui/badge";
import type { ProjectDocumentStatus } from "@/types/api";
import { cn } from "@/lib/utils";
import { useTranslations } from "next-intl";

type StatusBadgeProps = {
  status: ProjectDocumentStatus;
  className?: string;
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const t = useTranslations("Documents");
  const variant =
    status === "READY" ? "secondary" : status === "ERROR" ? "destructive" : "outline";
  const label =
    status === "READY"
      ? t("statusReady")
      : status === "ERROR"
        ? t("statusError")
        : t("statusIngesting");
  return (
    <Badge
      variant={variant}
      className={cn("font-normal", className)}
      data-ingestion-state={status}
      data-testid="document-status-badge"
    >
      {label}
    </Badge>
  );
}
