import { expect, type Page } from "@playwright/test";
import {
  installLayoutProductApiStub,
  seedLayoutActiveProject,
  addSmokeAccessCookie,
} from "./layout-product-api-stub";
import {
  LAYOUT_SMOKE_CONVERSATION_ID,
  LAYOUT_SMOKE_PROJECT_ID,
} from "../../support/layout-helpers";

const uxExperimentalPresets = [
  {
    productPresetId: "exp-preset-p0",
    code: "P0",
    label: "Corpus text only",
    description: "Baseline without retrieval",
    family: "RAG",
    supported: true,
    chatSelectable: true,
    labSelectable: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    protocolStageIndex: 0,
    requiredCapabilities: [],
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    labOnly: false,
  },
  {
    productPresetId: "exp-preset-p4",
    code: "P4",
    label: "Chunk + metadata retrieval",
    description: "Hybrid retrieval preset",
    family: "RAG",
    supported: true,
    chatSelectable: true,
    labSelectable: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    protocolStageIndex: 4,
    requiredCapabilities: [],
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    labOnly: false,
  },
];

const uxConfigSchema = {
  version: 1,
  fields: [
    { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
    { key: "llmModel", type: "string", userEditable: true },
  ],
};

const uxExperimentalDataset = {
  id: "550e8400-e29b-41d4-a716-446655440000",
  name: "Smoke workbook",
  experimentalDatasetType: "LLM_MODEL_BASELINE",
  readOnly: false,
  datasetType: "LLM_ONLY",
  validationStatus: "VALID",
  questionCounts: {
    llmReaderQuestions: 2,
    embeddingQueries: 0,
    ragPresetQuestions: 0,
    presetCatalog: 0,
    chunkRegistry: 0,
  },
  isReferenceBundle: false,
  isDemoDataset: false,
  canRunLlmBaseline: true,
  canRunEmbeddingBaseline: false,
  canRunRagPresetBenchmark: false,
  validationIssues: [],
  uploadedAt: "2026-01-01T00:00:00Z",
  description: null,
};

const uxLabChatEvalModels = {
  effectiveProvider: "OPENAI_COMPATIBLE",
  capability: "CHAT",
  models: [
    {
      modelName: "gpt-oss:20b",
      evalSelectable: true,
      blockedReason: null,
      blockedReasonCode: null,
      runtimeStatus: "AVAILABLE",
      embeddingDimensions: null,
      compatibleWithCurrentVectorStore: null,
      usableAsDefault: true,
    },
  ],
};

const uxSelectableModels = {
  effectiveProvider: "OPENAI_COMPATIBLE",
  capability: "CHAT",
  models: [
    {
      modelName: "gpt-oss:20b",
      displayName: "GPT OSS 20B",
      selectable: true,
      disabledReason: null,
      isDefault: true,
      runtimeStatus: "AVAILABLE",
    },
  ],
};

const uxLlmCatalog = {
  models: [
    {
      provider: "OPENAI_COMPATIBLE",
      capability: "CHAT",
      modelName: "gpt-oss:20b",
      displayName: "GPT OSS 20B",
      configured: true,
      available: true,
      selectableByUser: true,
      usableAsDefault: true,
      source: "PROPERTIES",
      runtimeStatus: "AVAILABLE",
      runtimeDetail: null,
      embeddingDimensions: null,
      compatibleWithCurrentVectorStore: null,
    },
    {
      provider: "OLLAMA_NATIVE",
      capability: "EMBEDDING",
      modelName: "wrong-dim-embed:latest",
      displayName: "Wrong dim embed",
      configured: true,
      available: true,
      selectableByUser: false,
      usableAsDefault: false,
      source: "PROPERTIES",
      runtimeStatus: "AVAILABLE",
      runtimeDetail: null,
      embeddingDimensions: 512,
      compatibleWithCurrentVectorStore: false,
    },
  ],
};

export type UxSurfacesStubOptions = {
  admin?: boolean;
};

/** Offline stubs for UX surface smoke (settings, chat config, lab exports, admin catalog). */
export async function installUxSurfacesApiStub(
  page: Page,
  options?: UxSurfacesStubOptions,
): Promise<void> {
  await installLayoutProductApiStub(page);
  await seedLayoutActiveProject(page);

  await page.route("**/api/v5/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v5/, "") || "/";
    const method = req.method();

    const fulfillJson = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
      });

    if (method === "GET" && path === "/auth/me") {
      await fulfillJson({
        userId: "smoke-user",
        email: "smoke@example.com",
        name: "Smoke User",
        roleName: options?.admin ? "ADMIN" : "USER",
        emailVerified: true,
        emailVerifiedAt: null,
      });
      return;
    }

    if (method === "GET" && path === "/config/schema") {
      await fulfillJson(uxConfigSchema);
      return;
    }

    if (method === "GET" && path === "/config/user") {
      await fulfillJson({ topK: 5, llmModel: "gpt-oss:20b" });
      return;
    }

    if (method === "GET" && path.startsWith("/me/llm/selectable-models")) {
      await fulfillJson(uxSelectableModels);
      return;
    }

    if (method === "GET" && path === "/chat/presets/catalog") {
      await fulfillJson({
        productPresets: [],
        experimentalPresets: uxExperimentalPresets,
      });
      return;
    }

    if (method === "GET" && path.match(/^\/conversations\/[^/]+$/)) {
      await fulfillJson({
        id: LAYOUT_SMOKE_CONVERSATION_ID,
        title: "Layout thread",
        updatedAt: "2026-01-01T00:00:00Z",
        llmModel: "gpt-oss:20b",
        classifierModelId: null,
        presetId: null,
        effectivePresetId: "default",
        documentFilter: [],
        runtimeOverride: {},
      });
      return;
    }

    if (method === "GET" && path.startsWith("/llm/catalog")) {
      await fulfillJson(uxLlmCatalog);
      return;
    }

    if (method === "GET" && path === "/lab/jobs/active") {
      await fulfillJson([]);
      return;
    }

    if (method === "GET" && path === "/lab/status") {
      await fulfillJson({
        referenceBundleAvailable: true,
        referenceBundleValid: true,
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: false, train: false, evaluate: false },
        message: "UX smoke stub",
      });
      return;
    }

    if (method === "GET" && path === "/lab/experimental-presets") {
      await fulfillJson([]);
      return;
    }

    if (method === "GET" && path === "/lab/experimental-datasets") {
      await fulfillJson([uxExperimentalDataset]);
      return;
    }

    if (method === "GET" && path.startsWith("/lab/evaluation-models")) {
      const capability = url.searchParams.get("capability");
      if (capability === "EMBEDDING") {
        await fulfillJson({ effectiveProvider: "OLLAMA_NATIVE", capability: "EMBEDDING", models: [] });
        return;
      }
      await fulfillJson(uxLabChatEvalModels);
      return;
    }

    if (method === "GET" && path.startsWith("/lab/benchmarks/LLM_JUDGE_QA/runs/latest")) {
      await fulfillJson({
        evaluationRunId: "smoke-run-id",
        jobId: "smoke-job-id",
        benchmarkKind: "LLM_JUDGE_QA",
        projectId: LAYOUT_SMOKE_PROJECT_ID,
        status: "SUCCEEDED",
        terminal: true,
        pollPath: "/lab/jobs/smoke-job-id",
        streamPath: "/lab/jobs/smoke-job-id/events",
        result: {},
        startedAt: "2026-01-01T00:01:00Z",
        completedAt: "2026-01-01T00:02:00Z",
        hasResults: true,
        campaignId: null,
      });
      return;
    }

    if (method === "GET" && path === "/lab/runs/smoke-run-id") {
      await fulfillJson({
        id: "smoke-run-id",
        status: "SUCCEEDED",
        benchmarkKind: "LLM_JUDGE_QA",
        campaignId: null,
      });
      return;
    }

    if (method === "GET" && path.includes("/lab/runs/smoke-run-id/export/")) {
      if (path.endsWith("/v1/results.json")) {
        await fulfillJson({ items: [{ id: "item-1", outcome: "EXECUTED" }] });
        return;
      }
      if (path.endsWith("/mvp/rollups.json")) {
        await fulfillJson({
          globalMacro: { outcomeCounts: { EXECUTED: 1 }, onExecuted: { n: 1, meanNormalizedExactMatch: 0.5 } },
        });
        return;
      }
      if (path.endsWith("/mvp/items.json")) {
        await fulfillJson({ items: [{ id: "item-1", outcome: "EXECUTED" }] });
        return;
      }
    }

    await route.fallback();
  });
}

export async function openChatConfiguration(page: import("@playwright/test").Page): Promise<void> {
  await page.goto(
    `/en/chat?projectId=${LAYOUT_SMOKE_PROJECT_ID}&conversationId=${LAYOUT_SMOKE_CONVERSATION_ID}`,
    { waitUntil: "domcontentloaded", timeout: 60_000 },
  );
  await expect(page.getByTestId(`conversation-item-${LAYOUT_SMOKE_CONVERSATION_ID}`)).toBeVisible({
    timeout: 20_000,
  });
  await page.getByTestId("chat-config-trigger").click();
  await expect(page.getByTestId("chat-configuration-side-panel")).toBeVisible({ timeout: 15_000 });
}

export { addSmokeAccessCookie, LAYOUT_SMOKE_PROJECT_ID, LAYOUT_SMOKE_CONVERSATION_ID };
