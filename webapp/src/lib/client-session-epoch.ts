/** Bumped on every client session reset so user-scoped query keys cannot reuse stale cache entries. */
let clientSessionEpoch = 0;

export function getClientSessionEpoch(): number {
  return clientSessionEpoch;
}

export function bumpClientSessionEpoch(): void {
  clientSessionEpoch += 1;
}
