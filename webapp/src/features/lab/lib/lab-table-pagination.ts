export const LAB_TABLE_PAGE_SIZE_OPTIONS = [25, 50, 100] as const;

export type LabTablePageSize = (typeof LAB_TABLE_PAGE_SIZE_OPTIONS)[number];

export const LAB_TABLE_DEFAULT_PAGE_SIZE: LabTablePageSize = 25;

export type PaginatedSlice<T> = {
  pageRows: T[];
  page: number;
  pageSize: LabTablePageSize;
  totalPages: number;
  totalRows: number;
  rangeStart: number;
  rangeEnd: number;
};

export function clampLabTablePage(page: number, totalPages: number): number {
  if (totalPages <= 0) return 1;
  return Math.min(Math.max(1, page), totalPages);
}

export function paginateRows<T>(
  rows: readonly T[],
  page: number,
  pageSize: LabTablePageSize,
): PaginatedSlice<T> {
  const totalRows = rows.length;
  const totalPages = totalRows === 0 ? 0 : Math.ceil(totalRows / pageSize);
  const safePage = totalPages === 0 ? 1 : clampLabTablePage(page, totalPages);
  const start = (safePage - 1) * pageSize;
  const end = Math.min(start + pageSize, totalRows);
  return {
    pageRows: rows.slice(start, end),
    page: safePage,
    pageSize,
    totalPages,
    totalRows,
    rangeStart: totalRows === 0 ? 0 : start + 1,
    rangeEnd: end,
  };
}

export function coerceLabTablePageSize(value: number): LabTablePageSize {
  if (LAB_TABLE_PAGE_SIZE_OPTIONS.includes(value as LabTablePageSize)) {
    return value as LabTablePageSize;
  }
  return LAB_TABLE_DEFAULT_PAGE_SIZE;
}
