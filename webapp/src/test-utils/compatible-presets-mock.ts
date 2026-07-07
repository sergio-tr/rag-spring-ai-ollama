/** Minimal compatible preset catalog for new-conversation dialog tests. */
import { P3_PRESET_ID } from "@/features/chat/lib/preset-product-selection";

export const compatiblePresetsQueryMock = {
  data: {
    projectId: "p1",
    effectiveEmbeddingModelId: "mxbai",
    hasActiveIndex: true,
    readyDocumentCount: 1,
    activeSnapshotCapabilities: {
      materializationStrategy: "CHUNK_LEVEL",
      supportsMetadata: false,
      embeddingModelId: "mxbai",
      chunkMaxChars: 400,
      chunkOverlap: 40,
    },
    productPresets: [
      {
        preset: {
          id: P3_PRESET_ID,
          name: "Chunk preset",
          description: null,
          tags: [],
          values: {},
          system: false,
          createdAt: "",
          updatedAt: "",
        },
        indexRequirements: null,
        compatibility: {
          selectable: true,
          disabledReasonCode: null,
          disabledReason: null,
          indexRequirements: null,
          compatibleWithActiveIndex: true,
        },
      },
    ],
    experimentalPresets: [],
  },
  isLoading: false,
  isError: false,
  isSuccess: true,
};

export const effectiveEmbeddingDefaultsMock = {
  data: {
    retrievalOptions: { topK: 12, similarityThreshold: 0.25, materializationStrategy: "CHUNK_LEVEL" },
  },
} as const;
