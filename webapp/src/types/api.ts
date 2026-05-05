/** Manual DTOs until OpenAPI codegen is wired. */

export type UserRole = "USER" | "ADMIN";

export type AuthUser = {
  id: string;
  email: string;
  name: string;
  role: UserRole;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
};

export type RegisterResponse = {
  status: "REGISTERED" | "PENDING_EMAIL_VERIFICATION";
  login?: LoginResponse | null;
};

export type MeResponse = {
  userId: string;
  email: string;
  name: string;
  roleName: UserRole;
  emailVerified: boolean;
  emailVerifiedAt: string | null;
};

export type ProjectSummary = {
  id: string;
  name: string;
  description?: string | null;
  docCount: number;
  convCount: number;
  updatedAt: string;
  projectPrompt?: string | null;
  colorHex?: string | null;
  iconKey?: string | null;
};

export type ProjectListResponse = {
  items: ProjectSummary[];
  total: number;
};

export type CreateProjectBody = {
  name: string;
  description?: string;
  initialPresetId?: string;
};

export type ActivateProjectResponse = {
  activeProjectId: string;
};

export type ProjectDocumentStatus = "INGESTING" | "READY" | "ERROR";

export type CorpusScope = "PROJECT_SHARED" | "CHAT_LOCAL";

export type ProjectDocumentDto = {
  id: string;
  fileName: string;
  status: ProjectDocumentStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  uploadedAt: string;
  reindexedAt: string | null;
  corpusScope: CorpusScope;
  conversationId: string | null;
  currentIndexSnapshotId: string | null;
  indexSignatureHash: string | null;
  storagePresent: boolean;
};

export type ConversationDto = {
  id: string;
  title: string;
  updatedAt: string;
  presetId?: string | null;
  /** When `presetId` is null, backend-resolved default preset id for UX (e.g. Demo_Best). */
  effectivePresetId?: string | null;
  /** Project document UUIDs limiting retrieval; empty = all documents in the project. */
  documentFilter?: string[];
};

export type RagPresetDto = {
  id: string;
  name: string;
  description: string | null;
  tags: string[];
  values: Record<string, unknown>;
  system: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AdminAllowlistEntryDto = {
  id: string;
  name: string;
  type: "LLM" | "EMBEDDING";
  inAllowlist: boolean;
  installedAt: string | null;
};

export type LabValidationIssueDto = {
  severity: string;
  code: string;
  sheet: string;
  rowNumber: number;
  column: string;
  message: string;
};

export type LabStatusResponse = {
  datasets: {
    enabled: boolean;
    /** @deprecated Legacy field — always null; do not use as source of truth. */
    legacyQuestionCountDeprecated?: number | null;
    datasetKindsReady?: boolean;
  };
  /** True when core typed dataset kinds in the internal reference workbook have non-zero row counts and validation passed. */
  datasetKindsReady?: boolean;
  /** Non-empty when the parser or workbook validator reported issues. */
  validationIssues?: LabValidationIssueDto[];
  /** True when the packaged reference workbook resource exists on the backend classpath. */
  referenceBundleAvailable?: boolean;
  /** True when the reference workbook parsed without validation errors (typed evaluation gate). */
  referenceBundleValid?: boolean;
  /** Optional protocol label extracted from the README sheet when present. */
  protocolVersion?: string;
  /** Parsed row counts per logical dataset kind (reference bundle). */
  countsByDatasetKind?: Record<string, number>;
  evaluations: {
    llm: boolean;
    rag: boolean;
    classifierProxy: boolean;
    asyncJobs?: boolean;
  };
  classifier: { configured: boolean; train: boolean; evaluate: boolean };
  message: string;
};

export type LabJobAcceptedDto = {
  jobId: string;
  status: string;
  pollPath: string;
  streamPath: string;
};

/** POST `{product}/me/account/export|deletion` → HTTP 202 (poll via `/me/account/jobs/{id}`, not Lab). */
export type AccountJobAcceptedDto = {
  jobId: string;
  status: string;
  pollPath: string;
};

export type MePreferencesResponse = {
  schemaVersion: number;
  preferences: Record<string, unknown>;
};

export type MePersonalizationResponse = {
  schemaVersion: number;
  personalization: Record<string, unknown>;
};

export type MeSummaryResponse = {
  projectCount: number;
  conversationCount: number;
  documentCount: number;
  estimatedStorageBytes: number;
};

export type UserDocumentRow = {
  documentId: string;
  projectId: string;
  conversationId: string | null;
  corpusScope: CorpusScope;
  fileName: string;
  status: ProjectDocumentStatus;
  uploadedAt: string;
  reindexedAt: string | null;
  indexSignatureHash: string | null;
  chunkCount: number | null;
  storagePresent: boolean;
};

export type MeDocumentsPageResponse = {
  items: UserDocumentRow[];
  total: number;
};

/** POST `{product}/lab/benchmarks/{kind}/runs` → HTTP 202 (canonical run + async task). */
export type BenchmarkKind =
  | "LLM_JUDGE_QA"
  | "EMBEDDING_RETRIEVAL"
  | "RAG_PRESET_END_TO_END"
  | "CLASSIFIER_METRICS";

export type BenchmarkJobAcceptedDto = {
  evaluationRunId: string;
  asyncTaskId: string;
  status: string;
  pollPath: string;
  streamPath: string;
};

export type StartBenchmarkRunRequest = {
  datasetId: string;
  projectId?: string | null;
  runKind?: "SCIENCE" | "PRODUCT_EXPLORATION" | "ADMIN_BASELINE";
  name?: string | null;
  resolvedConfigSnapshotId?: string | null;
  indexSnapshotId?: string | null;
  presetId?: string | null;
  experimentalPresetCodes?: string[] | null;
  /** Embedding benchmark: optionally run a fixed downstream answer step after retrieval. */
  embeddingDownstreamRag?: boolean | null;
  /** Optional Ollama chat model tag stored on the evaluation run. */
  llmModelId?: string | null;
  /** Optional Ollama embedding model tag stored on the evaluation run. */
  embeddingModelId?: string | null;
};

export type ExperimentalPresetCatalogItemDto = {
  productPresetId: string;
  code: string;
  family: string;
  label: string;
  description: string;
  requiredCapabilities: string[];
  supported: boolean;
  supportStatus: "EXECUTABLE" | "PARTIAL" | "NOT_SUPPORTED" | "REQUIRES_MULTI_TURN" | "DISABLED";
  reasonIfUnsupported: string | null;
  requiresMultiTurn: boolean;
  mapsToRuntimeCapabilities: Record<string, unknown>;
  allowedOutcomes: Array<"EXECUTED" | "NOT_SUPPORTED" | "FAILED" | "SKIPPED">;
  chatSelectable: boolean;
  labSelectable: boolean;
};

export type EvaluationRunDetailDto = {
  id: string;
  name: string | null;
  status: string;
  benchmarkKind: string | null;
  runKind: string | null;
  workflowSchemaVersion: string | null;
  datasetSha256: string | null;
  datasetId: string | null;
  asyncTaskId: string | null;
  resolvedConfigSnapshotId: string | null;
  indexSnapshotId: string | null;
  indexSignatureHash: string | null;
  presetId: string | null;
  llmModelId: string | null;
  embeddingModelId: string | null;
  classifierModelId: string | null;
  aggregatesJson: Record<string, unknown> | null;
  createdAt: string;
  completedAt: string | null;
};

export type CompareRunsResponseDto = {
  comparable: boolean;
  incompatibilityReasons: string[];
  runA: string;
  runB: string;
};

/** Optional query params for async Lab POSTs (scopes `async_task.project_id` when sent). */
export type LabProjectScopeParams = {
  projectId?: string;
};

export type AsyncTaskStatusDto = {
  id: string;
  taskType: string;
  status: string;
  progressText: string | null;
  result: Record<string, unknown> | null;
  errorMessage: string | null;
  terminal: boolean;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  /** Stable backend error code when a job fails (e.g. CHAT_DOCUMENT_SCOPE_EMPTY). */
  failureCode?: string | null;
};

export type MessageDto = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
  sources: Record<string, unknown>[] | null;
  queryType: string | null;
  pipelineSteps: Record<string, unknown>[] | null;
  /** Message lifecycle for assistant rows (USER is typically DONE). */
  status?: string | null;
  executionMetadata?: Record<string, unknown> | null;
};

export type StreamDonePayload = {
  answer: string;
  queryType: string | null;
  usedTool: boolean;
  toolUsed: string | null;
  sources: Record<string, unknown>[];
  pipelineSteps: Record<string, unknown>[];
};

/** POST `{product}/conversations/{id}/messages` (JSON body) → HTTP 202 + {@link LabJobAcceptedDto}. */
export type PostMessageBody = {
  content: string;
  llmModel?: string | null;
  /** Regenerate assistant only (user row already updated, e.g. after edit). */
  continueAfterUserMessageId?: string | null;
};

export type ConversationDraftDto = {
  content: string;
  updatedAt: string;
};

/** PATCH `{product}/conversations/{id}/messages/{messageId}` (user message body). */
export type PatchUserMessageBody = {
  content: string;
};

/** PATCH `{product}/conversations/{id}`. */
export type PatchConversationBody = {
  title?: string;
  presetId?: string | null;
  clearPreset?: boolean;
  documentFilter?: string[] | null;
};

/** GET `{product}/models` — allowlist vs Ollama tags. */
export type ModelsCatalogAllowlistEntry = {
  name: string;
  type: "LLM" | "EMBEDDING";
  inAllowlist: boolean;
  installedInOllama: boolean;
};

export type ModelsCatalogResponse = {
  ollamaReachable: boolean;
  installedModelNames: string[];
  allowlist: ModelsCatalogAllowlistEntry[];
};

/** GET {product}/lab/classifier/models */
export type ClassifierModelRegistryEntryDto = {
  id: string;
  name: string;
  inferenceTag: string;
  status: string;
  trainedAt: string | null;
  accuracy: number | null;
  f1Macro: number | null;
  active: boolean;
  hyperparams: Record<string, unknown>;
};

/** POST {product}/lab/classifier/models/{id}/activate */
export type ActivateClassifierModelBody = {
  projectId: string;
};

/** GET `{product}/lab/dataset-templates/{kind}` — Excel template for Lab uploads. */
export type ExperimentalDatasetTemplateKind =
  | "llm-model-baseline"
  | "embedding-baseline"
  | "rag-preset-benchmark"
  | "classifier-question-querytype";

export type ExperimentalDatasetValidationIssueDto = {
  severity: string;
  code: string;
  sheet: string;
  rowNumber: number;
  column: string;
  message: string;
};

export type ExperimentalDatasetValidationReportDto = {
  issues: ExperimentalDatasetValidationIssueDto[];
  hasErrors: boolean;
  hasWarnings: boolean;
};

/** HTTP 422 body from POST `{product}/lab/experimental-datasets` when the workbook is invalid. */
export type ExperimentalDatasetValidationFailedDto = {
  error: string;
  validationReport: ExperimentalDatasetValidationReportDto;
};

export type ExperimentalDatasetUploadResponseDto = {
  datasetId: string;
  experimentalDatasetType: string;
  persistedEvaluationDatasetType: string;
  validationStatus: string;
  questionCount: number;
  rowCount: number;
  validationReport: ExperimentalDatasetValidationReportDto;
};

export type ExperimentalDatasetListItemDto = {
  id: string;
  name: string | null;
  experimentalDatasetType: string;
  persistedEvaluationDatasetType: string;
  readOnly: boolean;
  questionCount: number | null;
  rowCount: number | null;
  validationStatus: string | null;
  uploadedAt: string;
  description: string | null;
};

/** GET `{product}/lab/runs/{runId}/items` — one evaluated row. */
export type EvaluationResultItemDto = {
  id: string;
  questionText: string;
  expectedAnswer: string;
  actualAnswer: string;
  correctness: number | null;
  queryType: string | null;
  latencyMs: number | null;
  benchmarkKind: string | null;
  metricsPayload: Record<string, unknown> | null;
  evaluatedAt: string;
};
