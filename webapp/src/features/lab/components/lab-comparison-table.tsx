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
  sortKey,
  sortState,
  onSort,
}: {
  children: ReactNode;
  className?: string;
  title?: string;
  sortKey?: string;
  sortState?: import("@/features/lab/lib/lab-table-sort").TableSortState;
  onSort?: (key: string) => void;
}) {
  const sortable = sortKey != null && onSort != null;
  const indicator =
    sortable && sortState?.key === sortKey ? (sortState.direction === "asc" ? " ▲" : " ▼") : "";
  if (!sortable) {
    return (
      <th className={`bg-background p-2 font-medium ${className}`} title={title}>
        {children}
      </th>
    );
  }
  return (
    <th className={`bg-background p-2 font-medium ${className}`} title={title}>
      <button
        type="button"
        className="hover:text-foreground inline-flex items-center gap-0.5 text-left font-medium"
        data-testid={`lab-sort-header-${sortKey}`}
        onClick={(event) => {
          event.stopPropagation();
          onSort(sortKey);
        }}
      >
        <span>
          {children}
          {indicator}
        </span>
      </button>
    </th>
  );
}
