export type SortDirection = "asc" | "desc";

export type TableSortState = {
  key: string;
  direction: SortDirection;
} | null;

/** Toggle sort: first click desc, second asc, third clears to default ordering. */
export function toggleTableSort(current: TableSortState, key: string): TableSortState {
  if (current?.key !== key) {
    return { key, direction: "desc" };
  }
  if (current.direction === "desc") {
    return { key, direction: "asc" };
  }
  return null;
}

export function sortDirectionIndicator(state: TableSortState, key: string): string {
  if (state?.key !== key) return "";
  return state.direction === "asc" ? " ▲" : " ▼";
}

function normalizeSortValue(value: unknown): string | number | null {
  if (value == null || value === "-" || value === "NOT_AVAILABLE") return null;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "boolean") return value ? 1 : 0;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    if (Number.isFinite(parsed) && /^-?\d+(\.\d+)?$/.test(trimmed)) {
      return parsed;
    }
    return trimmed.toLowerCase();
  }
  return String(value).toLowerCase();
}

export function compareSortValues(a: unknown, b: unknown): number {
  const av = normalizeSortValue(a);
  const bv = normalizeSortValue(b);
  if (av == null && bv == null) return 0;
  if (av == null) return 1;
  if (bv == null) return -1;
  if (typeof av === "number" && typeof bv === "number") return av - bv;
  return String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: "base" });
}

export function sortRowsByKey<T>(
  rows: T[],
  sort: TableSortState,
  accessor: (row: T, key: string) => unknown,
): T[] {
  if (!sort) return rows;
  return [...rows].sort((a, b) => {
    const cmp = compareSortValues(accessor(a, sort.key), accessor(b, sort.key));
    return sort.direction === "asc" ? cmp : -cmp;
  });
}
