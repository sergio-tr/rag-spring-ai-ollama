export type ProjectCreateMaterializationStrategy =
  | "CHUNK_LEVEL"
  | "DOCUMENT_LEVEL"
  | "HYBRID"
  | "STRUCTURED_SEARCH";

export type ProjectCreateIndexCombinationFeedback = {
  blocked: boolean;
  blockMessageKey?: "structuredSearchRequiresMetadata";
  warningMessageKey?: "hybridWithoutMetadataWarning" | "structuredSearchInfoWarning";
};

export function getProjectCreateIndexCombinationFeedback(
  strategy: ProjectCreateMaterializationStrategy,
  metadataEnabled: boolean,
): ProjectCreateIndexCombinationFeedback {
  if (strategy === "STRUCTURED_SEARCH" && !metadataEnabled) {
    return { blocked: true, blockMessageKey: "structuredSearchRequiresMetadata" };
  }
  if (strategy === "HYBRID" && !metadataEnabled) {
    return { blocked: false, warningMessageKey: "hybridWithoutMetadataWarning" };
  }
  if (strategy === "STRUCTURED_SEARCH" && metadataEnabled) {
    return { blocked: false, warningMessageKey: "structuredSearchInfoWarning" };
  }
  return { blocked: false };
}
