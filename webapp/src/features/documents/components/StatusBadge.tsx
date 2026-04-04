"use client";

import { Badge } from "@/components/ui/badge";
import type { ProjectDocumentStatus } from "@/types/api";
import { cn } from "@/lib/utils";

type StatusBadgeProps = {
  status: ProjectDocumentStatus;
  className?: string;
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const variant =
    status === "READY" ? "secondary" : status === "ERROR" ? "destructive" : "outline";
  return (
    <Badge variant={variant} className={cn("font-normal", className)}>
      {status}
    </Badge>
  );
}
