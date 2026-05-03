/** Backend puts UUID string under {@link AccountJobPayloadKeys.EXPORT_ARTIFACT_ID}. */
export function pickExportArtifactId(result: Record<string, unknown> | null): string | undefined {
  if (!result) return undefined;
  const raw = result.exportArtifactId;
  return typeof raw === "string" && raw.length > 0 ? raw : undefined;
}
