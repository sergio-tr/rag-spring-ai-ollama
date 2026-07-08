import type { Page } from "@playwright/test";
import {
  LAYOUT_SMOKE_CONVERSATION_ID,
  LAYOUT_SMOKE_PROJECT_ID,
} from "../../support/layout-helpers";
import { addSmokeAccessCookie } from "./minimal-product-api-stub";

const layoutProject = {
  id: LAYOUT_SMOKE_PROJECT_ID,
  name: "Layout Smoke",
  iconKey: null,
  colorHex: "#6366f1",
  docCount: 0,
  convCount: 1,
  updatedAt: "2026-01-01T00:00:00Z",
};

const layoutConversation = {
  id: LAYOUT_SMOKE_CONVERSATION_ID,
  title: "Layout thread",
  updatedAt: "2026-01-01T00:00:00Z",
  llmModel: "llama3.2",
  classifierModelId: null,
  presetId: null,
  effectivePresetId: "default",
  documentFilter: [] as string[],
  runtimeOverride: {},
};

function layoutRuntimeState(conversationId: string) {
  return {
    conversationId,
    selectedPresetId: null,
    effectivePresetId: "default",
    preset: {
      kind: "DEFAULT" as const,
      code: null,
      label: "Default",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig: { useRetrieval: true, rankerEnabled: false, memoryEnabled: false },
    effectiveConfig: { useRetrieval: true, rankerEnabled: false, memoryEnabled: false },
    conversationLlmModel: "llama3.2",
    conversationClassifierModelId: null,
    conversationModelsPinned: false,
    runtimeOverride: {},
    manualOverrideKeys: [] as string[],
    isCustom: false,
    validation: { valid: true, supported: true, errors: [], warnings: [] },
    isValid: true,
    blockingIssues: [],
    warnings: [],
    selectedWorkflow: "dense_chunk_workflow",
    indexCompatibility: null,
    requiresReindex: false,
    disabledRuntimeFeatures: [],
    disabledPresetReason: null,
  };
}

/**
 * Offline stub for layout scroll tests: active project, one conversation, empty thread.
 */
export async function installLayoutProductApiStub(page: Page): Promise<void> {
  const meJson = {
    userId: "smoke-user",
    email: "smoke@example.com",
    name: "Smoke User",
    roleName: "USER",
    emailVerified: true,
    emailVerifiedAt: null,
  };

  const labStatusJson = {
    referenceBundleAvailable: false,
    referenceBundleValid: false,
    datasetKindsReady: false,
    countsByDatasetKind: {
      llmReaderQuestions: 0,
      embeddingRetrievalQueries: 0,
      ragPresetQuestions: 0,
    },
    datasets: { enabled: false, datasetKindsReady: false },
    evaluations: { llm: false, rag: false, classifierProxy: false, asyncJobs: false },
    classifier: { configured: false, train: false, evaluate: false },
    message: "Offline layout stub",
  };

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

    if (method === "POST" && path === "/auth/refresh") {
      await fulfillJson({
        accessToken: "smoke-access-token",
        refreshToken: "smoke-refresh-token",
        user: { id: "smoke-user", email: "smoke@example.com", name: "Smoke User", role: "USER" },
      });
      return;
    }

    if (method !== "GET") {
      await fulfillJson({});
      return;
    }

    if (path === "/auth/me") {
      await fulfillJson(meJson);
      return;
    }

    if (path === "/projects") {
      await fulfillJson({ items: [layoutProject], total: 1 });
      return;
    }

    const convListMatch = path.match(/^\/projects\/([^/]+)\/conversations$/);
    if (convListMatch) {
      await fulfillJson([layoutConversation]);
      return;
    }

    const messagesMatch = path.match(/^\/conversations\/([^/]+)\/messages$/);
    if (messagesMatch) {
      await fulfillJson([]);
      return;
    }

    const runtimeMatch = path.match(/^\/conversations\/([^/]+)\/runtime-state$/);
    if (runtimeMatch) {
      await fulfillJson(layoutRuntimeState(runtimeMatch[1]));
      return;
    }

    const indexProfileMatch = path.match(/^\/projects\/([^/]+)\/index-profile$/);
    if (indexProfileMatch) {
      await fulfillJson({
        projectId: indexProfileMatch[1],
        materializationStrategy: "CHUNK_LEVEL",
        metadataEnabled: false,
        metadataProfile: null,
        embeddingModelId: "mxbai-embed-large",
        chunkMaxChars: 400,
        chunkOverlap: null,
        profileHash: "layout-hash",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      });
      return;
    }

    const compatiblePresetsMatch = path.match(/^\/projects\/([^/]+)\/compatible-presets/);
    if (compatiblePresetsMatch) {
      await fulfillJson({
        projectId: compatiblePresetsMatch[1],
        effectiveEmbeddingModelId: "mxbai-embed-large",
        hasActiveIndex: false,
        readyDocumentCount: 0,
        activeSnapshotCapabilities: null,
        productPresets: [],
        experimentalPresets: [],
      });
      return;
    }

    const activeSnapshotMatch = path.match(/^\/projects\/([^/]+)\/knowledge\/snapshots\/active$/);
    if (activeSnapshotMatch) {
      await fulfillJson(null);
      return;
    }

    if (path === "/lab/classifier/models") {
      await fulfillJson([]);
      return;
    }

    const docsMatch = path.match(/^\/projects\/([^/]+)\/documents$/);
    if (docsMatch) {
      // Contract: ProjectDocumentDto[] (see useProjectDocuments / useProjectDocumentsForConversation).
      await fulfillJson([]);
      return;
    }

    const draftMatch = path.match(/^\/conversations\/([^/]+)\/draft$/);
    if (draftMatch) {
      await fulfillJson({ content: "", updatedAt: "2026-01-01T00:00:00Z" });
      return;
    }

    if (path === "/models" || path.startsWith("/models?")) {
      const selectable = [
        {
          modelId: "llama3.2",
          displayName: "Llama 3.2",
          type: "LLM",
          tags: [],
          available: true,
          lastCheckedAt: null,
        },
      ];
      if (path.includes("type=")) {
        await fulfillJson(selectable);
        return;
      }
      await fulfillJson({
        ollamaReachable: false,
        installedModelNames: ["llama3.2"],
        allowlist: selectable,
      });
      return;
    }

    if (path === "/chat/presets/catalog") {
      await fulfillJson({ productPresets: [], experimentalPresets: [] });
      return;
    }

    if (path === "/config/schema") {
      await fulfillJson({ version: 1, fields: [] });
      return;
    }

    if (path === "/config/user") {
      await fulfillJson({ topK: 5, llmModel: "llama3.2" });
      return;
    }

    if (path.startsWith("/me/llm/selectable-models")) {
      await fulfillJson({
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
      });
      return;
    }

    if (path === "/me/preferences") {
      await fulfillJson({ schemaVersion: 1, preferences: {} });
      return;
    }

    if (path === "/me/personalization") {
      await fulfillJson({ schemaVersion: 1, personalization: {} });
      return;
    }

    if (path === "/me/summary") {
      await fulfillJson({
        projectCount: 1,
        conversationCount: 1,
        documentCount: 0,
        estimatedStorageBytes: 0,
      });
      return;
    }

    if (path.startsWith("/me/documents")) {
      await fulfillJson({ items: [], total: 0 });
      return;
    }

    if (path === "/lab/status") {
      await fulfillJson(labStatusJson);
      return;
    }

    if (path === "/lab/jobs/active") {
      await fulfillJson([]);
      return;
    }

    if (path === "/lab/experimental-datasets") {
      await fulfillJson([]);
      return;
    }

    if (path === "/presets") {
      await fulfillJson([]);
      return;
    }

    if (path === "/model-registry") {
      await fulfillJson({
        ollamaReachable: false,
        ollamaErrorMessage: "offline layout stub",
        llmModels: [],
        embeddingModels: [],
      });
      return;
    }

    await fulfillJson({});
  });
}

export async function seedLayoutActiveProject(page: Page): Promise<void> {
  await page.addInitScript(
    ({ projectId, projectName, colorHex }) => {
      const key = "rag-app";
      const raw = localStorage.getItem(key);
      const parsed = raw ? (JSON.parse(raw) as { state?: Record<string, unknown> }) : { state: {} };
      parsed.state = {
        ...parsed.state,
        activeProject: {
          id: projectId,
          name: projectName,
          iconKey: null,
          colorHex,
        },
      };
      localStorage.setItem(key, JSON.stringify(parsed));
    },
    {
      projectId: LAYOUT_SMOKE_PROJECT_ID,
      projectName: layoutProject.name,
      colorHex: layoutProject.colorHex,
    },
  );
}

export { addSmokeAccessCookie };
