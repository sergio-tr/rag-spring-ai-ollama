/**
 * Unique project names for parallel workers (avoid collisions on shared DB).
 */

export function uniqueProjectName(prefix = "e2e"): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
}
