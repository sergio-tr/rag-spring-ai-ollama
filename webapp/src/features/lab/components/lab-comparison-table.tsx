"use client";

import type { ReactNode } from "react";

export type LabComparisonTableProps = {
  testId?: string;
  children: ReactNode;
  maxHeightClassName?: string;
};

/** Shared scroll container with opaque sticky headers for Lab comparison tables. */
export function LabComparisonTable({
  testId,
  children,
  maxHeightClassName = "max-h-56",
}: LabComparisonTableProps) {
  return (
    <div
      className={`${maxHeightClassName} overflow-auto rounded-md border`}
      data-testid={testId}
    >
      <table className="w-full border-separate border-spacing-0 text-left text-xs">{children}</table>
    </div>
  );
}

export function LabComparisonTableHead({ children }: { children: ReactNode }) {
  return (
    <thead className="bg-background sticky top-0 z-20 shadow-[0_1px_0_0_hsl(var(--border))]">
      {children}
    </thead>
  );
}

export function LabComparisonTh({
  children,
  className = "",
  title,
}: {
  children: ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <th className={`bg-background p-2 font-medium ${className}`} title={title}>
      {children}
    </th>
  );
}
