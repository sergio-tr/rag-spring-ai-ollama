"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Link } from "@/navigation";
import type { ReactNode } from "react";

export type CompactHelpProps = Readonly<{
  summary: string;
  children: ReactNode;
  testId?: string;
  className?: string;
}>;

/** Collapsed help; long copy stays hidden until expanded. */
export function CompactHelp({ summary, children, testId, className }: CompactHelpProps) {
  return (
    <details className={className ?? "text-muted-foreground text-xs"} data-testid={testId}>
      <summary className="cursor-pointer font-medium text-foreground">{summary}</summary>
      <div className="mt-2 space-y-2">{children}</div>
    </details>
  );
}

export type TechnicalDetailsProps = Readonly<{
  summary: string;
  children: ReactNode;
  testId?: string;
  className?: string;
}>;

/** Collapsed technical / server / JSON details. */
export function TechnicalDetails({ summary, children, testId, className }: TechnicalDetailsProps) {
  return (
    <details
      className={className ?? "rounded-md border bg-muted/20 p-3 text-xs"}
      data-testid={testId}
    >
      <summary className="cursor-pointer font-medium text-foreground">{summary}</summary>
      <div className="mt-3 space-y-3">{children}</div>
    </details>
  );
}

export type RunSummaryCardProps = Readonly<{
  title: string;
  summary?: string;
  status?: ReactNode;
  footer?: ReactNode;
  testId?: string;
}>;

/** Compact page header: title, one-line summary, optional readiness status. */
export function RunSummaryCard({ title, summary, status, footer, testId }: RunSummaryCardProps) {
  return (
    <Card data-testid={testId ?? "lab-run-summary-card"}>
      <CardHeader className="gap-1 pb-2">
        <CardTitle className="text-lg">{title}</CardTitle>
        {summary ? <p className="text-muted-foreground text-xs">{summary}</p> : null}
      </CardHeader>
      {status || footer ? (
        <CardContent className="space-y-2 pt-0 text-xs">
          {status}
          {footer}
        </CardContent>
      ) : null}
    </Card>
  );
}

export type LabWorkflowCardProps = Readonly<{
  title: string;
  tagline: string;
  statusLabel: string;
  statusVariant?: "default" | "secondary" | "destructive" | "outline";
  href: string;
  cta: string;
  testId: string;
}>;

/** LAB home workflow entry: short copy, status badge, open action. */
export function LabWorkflowCard({
  title,
  tagline,
  statusLabel,
  statusVariant = "secondary",
  href,
  cta,
  testId,
}: LabWorkflowCardProps) {
  return (
    <Card className="flex flex-col" data-testid={testId}>
      <CardHeader className="gap-1 pb-2">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-base">{title}</CardTitle>
          <Badge variant={statusVariant} className="shrink-0 text-[10px]">
            {statusLabel}
          </Badge>
        </div>
        <p className="text-muted-foreground text-xs leading-snug">{tagline}</p>
      </CardHeader>
      <CardContent className="mt-auto pt-0">
        <Link
          className="text-primary inline-flex text-xs font-medium underline-offset-4 hover:underline"
          href={href}
        >
          {cta}
        </Link>
      </CardContent>
    </Card>
  );
}
