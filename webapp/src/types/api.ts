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

export type ProjectIndexProfileSummary = {
  projectId: string;
  materializationStrategy: string | null;
  metadataEnabled: boolean;
  metadataProfile: string | null;
  embeddingModelId: string | null;
  chunkMaxChars: number;
  chunkOverlap: number | null;
  profileHash: string;
  createdAt: string;
  updatedAt: string;
};

export type UpsertProjectIndexProfileBody = {
  materializationStrategy?: string | null;
  metadataEnabled?: boolean | null;
  metadataProfile?: string | null;
  embeddingModelId?: string | null;
  chunkMaxChars?: number | null;
  chunkOverlap?: number | null;
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
  /** Present on POST create / GET detail; omitted on list to avoid extra joins. */
  indexProfile?: ProjectIndexProfileSummary | null;
};

export type ProjectListResponse = {
  items: ProjectSummary[];
  total: number;
};

export type CreateProjectBody = {
  name: string;
  description?: string;
  initialPresetId?: string;
  initialIndexProfile?: UpsertProjectIndexProfileBody | null;
};

/** POST /projects/{id}/conversations optional seed payload */
export type CreateConversationBody = {
  title?: string | null;
  documentFilter?: string[];
  initialPresetId?: string | null;
  initialRuntimeOverride?: Record<string, unknown> | null;
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
  /** Persisted per-conversation LLM id; empty UI means server default chain applies. */
  llmModel?: string | null;
  /** Persisted per-conversation classifier inference tag; null defers to project RAG JSON. */
  classifierModelId?: string | null;
  /** Project document UUIDs limiting retrieval; empty = all documents in the project. */
  documentFilter?: string[];
  /** Conversation-scoped runtime override keys (merged on top of preset + project config). */
  runtimeOverride?: Record<string, unknown>;
  /** Populated when returned from POST create after validation preview; omitted or empty on list. */
  effectiveRuntimePreview?: Record<string, unknown>;
  runtimeWarnings?: RuntimeConfigValidationIssueDto[];
  indexCompatibility?: RuntimeIndexCompatibilityDto | null;
  /** Backend clarification wait-state (`pending_clarification_jsonb`) when follow-up is expected. */
  pendingClarification?: Record<string, unknown> | null;
};

export type ChatPresetSummaryDto = {
  kind: "PRODUCT" | "EXPERIMENTAL" | "DEFAULT" | "MISSING";
  code: string | null;
  label: string;
  chatSelectable: boolean;
  supported: boolean;
  supportStatus: string | null;
  reasonIfUnsupported: string | null;
};

export type ChatRuntimeValidationDto = {
  valid: boolean;
  supported: boolean;
  errors: RuntimeConfigValidationIssueDto[];
  warnings: RuntimeConfigValidationIssueDto[];
};

export type ChatRuntimeStateDto = {
  conversationId: string;
  selectedPresetId: string | null;
  effectivePresetId: string | null;
  preset: ChatPresetSummaryDto;
  baseEffectiveConfig: Record<string, unknown>;
  effectiveConfig: Record<string, unknown>;
  conversationLlmModel: string | null;
  conversationClassifierModelId: string | null;
  conversationModelsPinned: boolean;
  runtimeOverride: Record<string, unknown>;
  manualOverrideKeys: string[];
  isCustom: boolean;
  validation: ChatRuntimeValidationDto;
  selectedWorkflow: string | null;
  indexCompatibility: ConversationDto["indexCompatibility"];
  requiresReindex: boolean;
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

export type AdminModelEntryDto = {
  id: string;
  modelId: string;
  displayName: string | null;
  modelType: "LLM" | "EMBEDDING";
  enabled: boolean;
  available: boolean;
  lastCheckedAt: string | null;
  lastPullStatus: string | null;
  lastPullError: string | null;
  installedAt: string | null;
  tags: string[] | null;
};

export type AdminModelCheckRequest = {
  modelId: string;
  modelType: "LLM" | "EMBEDDING";
  pullIfMissing: boolean;
};

export type AdminModelCheckResponse = {
  modelId: string;
  requestedType: "LLM" | "EMBEDDING";
  existsLocal: boolean;
  canPull: boolean;
  pulled: boolean;
  embeddingProbeOk: boolean;
  matchedLocalIds: string[];
  checkedAt: string;
  errorCode: string | null;
  errorMessage: string | null;
  pullSummary: string | null;
};

export type AdminModelUpsertRequest = {
  modelId: string;
  displayName?: string | null;
  modelType: "LLM" | "EMBEDDING";
  enabled: boolean;
  pullIfMissing: boolean;
  tags?: string[] | null;
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

export type ActiveLabJobDto = {
  jobId: string;
  benchmarkKind: string | null;
  evaluationRunId: string;
  projectId: string | null;
  datasetId: string | null;
  status: string;
  progress: string | null;
  startedAt: string | null;
  updatedAt: string | null;
  pollPath: string | null;
  streamPath: string | null;
  cancellable: boolean;
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
  /** Present when the request starts a multi-run campaign (e.g. multi-LLM). */
  campaignId?: string | null;
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
  /** Optional multi-LLM campaign: one run per model id. */
  llmModelIds?: string[] | null;
  /** Optional multi-embedding campaign (backend may reject if unsupported). */
  embeddingModelIds?: string[] | null;
  /** When true, let the backend use workbook-derived candidates when supported. */
  useWorkbookCandidates?: boolean | null;
  /** Optional user-facing name for campaign grouping. */
  campaignName?: string | null;
};

export type ExperimentalPresetCatalogItemDto = {
  productPresetId: string;
  code: string;
  family: string;
  label: string;
  description: string;
  indexRequirements?: {
    requiredMaterializationStrategy: string | null;
    requiresMetadataSupport: boolean;
  } | null;
  requiredCapabilities: string[];
  supported: boolean;
  supportStatus:
    | "EXECUTABLE"
    | "PARTIAL"
    | "NOT_SUPPORTED"
    | "REQUIRES_MULTI_TURN"
    | "DISABLED"
    | "FUTURE_MULTI_TURN_NOT_SELECTABLE";
  reasonIfUnsupported: string | null;
  requiresMultiTurn: boolean;
  mapsToRuntimeCapabilities: Record<string, unknown>;
  allowedOutcomes: Array<"EXECUTED" | "NOT_SUPPORTED" | "FAILED" | "SKIPPED">;
  chatSelectable: boolean;
  labSelectable: boolean;
  /** Derived: `labSelectable && !chatSelectable` */
  labOnly: boolean;
  corpusRequired?: boolean;
  requiresSnapshot?: boolean;
  requiresProjectDocuments?: boolean;
  singleTurnBenchmarkSelectable?: boolean;
  /** P0=0 … P14=14 */
  protocolStageIndex?: number;
  parentPresetCode?: string | null;
  /** Canonical terminal runtime JSON (Lab + Chat overlay). */
  effectiveTerminalRuntimeJson?: string;
};

export type RuntimeConfigCapabilityDto = {
  key: string;
  label: string;
  description: string;
  category: "RUNTIME_HOT_SWAPPABLE" | "INDEX_BOUND" | "LAB_ONLY" | "INTERNAL";
  visibleInChat: boolean;
  configurableInChat: boolean;
  implemented: boolean;
  engineWired: boolean;
  supportMode: string | null;
  displayOrder: number;
  requires: string[];
  excludes: string[];
  requiresIndexSnapshot: boolean;
  requiresReindexWhenChanged: boolean;
  reasonIfDisabled: string | null;
  reasonIfNotImplemented: string | null;
};

export type RuntimeConfigCapabilitiesResponse = {
  capabilities: RuntimeConfigCapabilityDto[];
};

export type RuntimeConfigValidationIssueDto = {
  code: string;
  field: string | null;
  message: string;
  severity: "ERROR" | "WARNING";
};

export type RuntimeConfigValidateRequest = {
  conversationId: string;
  presetId?: string | null;
  experimentalPresetCode?: string | null;
  overrides?: Record<string, unknown> | null;
};

export type RuntimeConfigValidateResponse = {
  valid: boolean;
  supported: boolean;
  effectiveConfig: Record<string, unknown>;
  errors: RuntimeConfigValidationIssueDto[];
  warnings: RuntimeConfigValidationIssueDto[];
  selectedWorkflow: string | null;
  indexCompatibility?: RuntimeIndexCompatibilityDto | null;
  requiresReindex?: boolean;
};

export type RuntimeIndexCompatibilityDto = {
  activeProjectSnapshotId: string | null;
  activeConversationSnapshotId: string | null;
  activeIndexProfileHash: string | null;
  activeIndexProfile: Record<string, unknown>;
  hasActiveIndex: boolean;
  activeSnapshotCapabilities?: {
    materializationStrategy: string | null;
    supportsMetadata: boolean | null;
    embeddingModelId: string | null;
    chunkMaxChars: number | null;
    chunkOverlap: number | null;
  } | null;
  presetIndexRequirements?: {
    requiredMaterializationStrategy: string | null;
    requiresMetadataSupport: boolean;
  } | null;
  compatibleWithPreset?: boolean;
  compatibilityStatus?: string | null;
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

export type ChatSourceDto = {
  documentId: string | null;
  projectDocumentId: string | null;
  filename: string | null;
  snippet: string | null;
  /** Smaller is closer (embedding distance). */
  distance: number | null;
  /** Fixed label for now (future: score/similarity). */
  distanceLabel: "distance" | string | null;
  chunkIndex: number | null;
  /** Optional, only when backend provides a detected/known date. */
  detectedDate: string | null;
  metadata: Record<string, unknown> | null;
};

export type MessageDto = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
  sources: ChatSourceDto[] | null;
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
  sources: ChatSourceDto[];
  pipelineSteps: Record<string, unknown>[];
  /** Privacy-safe runtime hints mirrored from the last assistant message metadata. */
  runtimeTelemetry?: Record<string, unknown>;
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
  runtimeOverride?: Record<string, unknown> | null;
  clearRuntimeOverride?: boolean;
  clearPendingClarification?: boolean;
  clearLlmModel?: boolean;
  llmModel?: string | null;
  clearClassifierModelId?: boolean;
  classifierModelId?: string | null;
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

/** GET `{product}/models?type=LLM|EMBEDDING` — filtered selectable models. */
export type SelectableModelDto = {
  modelId: string;
  displayName: string | null;
  type: "LLM" | "EMBEDDING";
  tags: string[];
  available: boolean;
  lastCheckedAt: string | null;
};

/** GET `{product}/model-registry` — curated demo LLM/embedding targets + Ollama presence (no allowlist writes). */
export type ModelRegistryAvailabilityStatus = "AVAILABLE" | "MISSING" | "ERROR";

export type ModelRegistryItemDto = {
  modelId: string;
  modelType: "LLM" | "EMBEDDING";
  status: ModelRegistryAvailabilityStatus;
  detail: string | null;
  embeddingCompatible: boolean | null;
};

export type ModelRegistryResponseDto = {
  ollamaReachable: boolean;
  ollamaErrorMessage: string | null;
  llmModels: ModelRegistryItemDto[];
  embeddingModels: ModelRegistryItemDto[];
};

/** POST `{product}/model-registry/check` */
export type ModelRegistryCheckRequest = {
  modelId: string;
  /** When false, skips the embedding HTTP probe for embedding rows. */
  probeEmbedding?: boolean | null;
};

/** POST `{product}/model-registry/pull` — only curated ids; returns lab job envelope. */
export type ModelRegistryPullRequest = {
  modelId: string;
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

export type ExperimentalDatasetQuestionCountsDto = {
  llmReaderQuestions: number;
  embeddingQueries: number;
  ragPresetQuestions: number;
  presetCatalog: number;
  chunkRegistry: number;
};

export type ExperimentalDatasetListItemDto = {
  id: string;
  name: string | null;
  experimentalDatasetType: string;
  readOnly: boolean;
  datasetType: string;
  validationStatus: string;
  questionCounts: ExperimentalDatasetQuestionCountsDto;
  isReferenceBundle: boolean;
  isDemoDataset: boolean;
  canRunLlmBaseline: boolean;
  canRunEmbeddingBaseline: boolean;
  canRunRagPresetBenchmark: boolean;
  validationIssues: ExperimentalDatasetValidationIssueDto[];
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
