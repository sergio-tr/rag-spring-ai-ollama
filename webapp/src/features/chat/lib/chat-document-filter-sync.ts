/** Stable equality for conversation document filters (order-independent). */
export function documentFiltersEqual(a: readonly string[] | undefined, b: readonly string[] | undefined): boolean {
  const as = [...(a ?? [])].sort().join("\0");
  const bs = [...(b ?? [])].sort().join("\0");
  return as === bs;
}
