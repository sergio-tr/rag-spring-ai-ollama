import type { ProjectDocumentStatus } from "@/types/api";

/** Maps API status + optional error to a short UI label (ERROR shown as Failed). */
export function documentStatusLabel(status: ProjectDocumentStatus): string {
  if (status === "READY") return "Ready";
  if (status === "ERROR") return "Failed";
  return "Processing";
}

export function isTerminalDocumentStatus(status: ProjectDocumentStatus): boolean {
  return status === "READY" || status === "ERROR";
}
