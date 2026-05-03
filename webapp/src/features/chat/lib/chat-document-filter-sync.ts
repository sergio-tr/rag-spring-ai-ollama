/** Stable equality for conversation document filters (order-independent). */
export function documentFiltersEqual(a: readonly string[] | undefined, b: readonly string[] | undefined): boolean {
  const cmp = (x: string, y: string) => x.localeCompare(y);
  const as = [...(a ?? [])].sort(cmp).join("\0");
  const bs = [...(b ?? [])].sort(cmp).join("\0");
  return as === bs;
}
