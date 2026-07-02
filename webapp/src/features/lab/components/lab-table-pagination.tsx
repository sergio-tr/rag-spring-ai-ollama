"use client";

import { Button } from "@/components/ui/button";
import {
  coerceLabTablePageSize,
  LAB_TABLE_DEFAULT_PAGE_SIZE,
  LAB_TABLE_PAGE_SIZE_OPTIONS,
  type LabTablePageSize,
} from "@/features/lab/lib/lab-table-pagination";
import { useTranslations } from "next-intl";
import { useState } from "react";

export type UseLabTablePaginationOptions = {
  rowCount: number;
  defaultPageSize?: LabTablePageSize;
  resetKey?: string;
};

export function useLabTablePagination({
  rowCount,
  defaultPageSize = LAB_TABLE_DEFAULT_PAGE_SIZE,
  resetKey,
}: UseLabTablePaginationOptions) {
  const [pageSize, setPageSize] = useState<LabTablePageSize>(defaultPageSize);
  const resetToken = `${resetKey ?? ""}:${pageSize}`;
  const [pageState, setPageState] = useState({ token: resetToken, page: 1 });

  const totalPages = rowCount === 0 ? 0 : Math.ceil(rowCount / pageSize);

  let page = pageState.token === resetToken ? pageState.page : 1;
  if (pageState.token !== resetToken) {
    page = 1;
    setPageState({ token: resetToken, page: 1 });
  } else if (totalPages > 0 && page > totalPages) {
    page = totalPages;
    if (pageState.page !== totalPages) {
      setPageState({ token: resetToken, page: totalPages });
    }
  }

  const setPage = (next: number) => setPageState({ token: resetToken, page: next });

  return {
    page,
    pageSize,
    totalPages,
    setPage,
    setPageSize,
  };
}

export type LabTablePaginationBarProps = {
  page: number;
  pageSize: LabTablePageSize;
  totalRows: number;
  totalPages: number;
  rangeStart: number;
  rangeEnd: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: LabTablePageSize) => void;
  testId?: string;
};

export function LabTablePaginationBar({
  page,
  pageSize,
  totalRows,
  totalPages,
  rangeStart,
  rangeEnd,
  onPageChange,
  onPageSizeChange,
  testId = "lab-table-pagination",
}: LabTablePaginationBarProps) {
  const t = useTranslations("Lab");

  if (totalRows === 0) {
    return null;
  }

  return (
    <div
      className="flex flex-wrap items-center justify-between gap-2 text-xs"
      data-testid={testId}
    >
      <p className="text-muted-foreground">
        {t("benchmarkTablePaginationSummary", {
          start: rangeStart,
          end: rangeEnd,
          total: totalRows,
        })}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-1">
          <span className="text-muted-foreground">{t("benchmarkTablePageSize")}</span>
          <select
            className="bg-background rounded-md border px-2 py-1"
            data-testid={`${testId}-page-size`}
            value={pageSize}
            onChange={(event) => onPageSizeChange(coerceLabTablePageSize(Number(event.target.value)))}
          >
            {LAB_TABLE_PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>
        <span className="text-muted-foreground font-mono">
          {t("benchmarkTablePageOf", { page, totalPages: Math.max(totalPages, 1) })}
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid={`${testId}-prev`}
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          {t("benchmarkTablePrev")}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid={`${testId}-next`}
          disabled={totalPages === 0 || page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          {t("benchmarkTableNext")}
        </Button>
      </div>
    </div>
  );
}
