import * as fs from "node:fs";
import * as path from "node:path";
import { expect, type APIRequestContext } from "@playwright/test";
import { authHeaders } from "./auth";
import { assertBodyNotHtml } from "./json-contract";
import { productUrl } from "./env";

export type ProjectDocumentDto = {
  id: string;
  fileName: string;
  status: "INGESTING" | "READY" | "ERROR";
  chunkCount?: number | null;
  errorMessage?: string | null;
};

/** Canonical acceptance acta PDFs from classpath docs (evaluation / functional defense). */
export const ACTA_ACCEPTANCE_FIXTURES_DIR = path.join(
  process.cwd(),
  "..",
  "rag-service",
  "src",
  "main",
  "resources",
  "docs",
);

export const ACTA_ACCEPTANCE_MINIMAL_FILES = ["ACTA 5.pdf"] as const;
export const ACTA_ACCEPTANCE_EXTENDED_FILES = [
  "ACTA 1.pdf",
  "ACTA 2.pdf",
  "ACTA 3.pdf",
  "ACTA 5.pdf",
  "ACTA 6.pdf",
] as const;

export type LabJobAccepted = {
  jobId: string;
  status: string;
  pollPath: string;
  streamPath: string;
};

export type LabJobStatusBody = {
  id: string;
  taskType: string;
  status: string;
  progressText: string | null;
  result: Record<string, unknown> | null;
  errorMessage: string | null;
  terminal: boolean;
  failureCode?: string | null;
};

export type ConversationDto = {
  id: string;
  presetId?: string | null;
  effectivePresetId?: string | null;
  documentFilter?: string[];
};

export type RagPresetDto = {
  id: string;
  name: string;
};

export type ChatPresetCatalogDto = {
  productPresets: RagPresetDto[];
  experimentalPresets: Array<{ id: string; code?: string; label?: string }>;
};

export type MessageDto = {
  id: string;
  role: string;
  content: string;
  executionMetadata?: Record<string, unknown>;
  sources?: unknown[];
};

export const DEMO_BEST_PRESET_ID = "cafe0001-0001-4001-8001-000000000003";

/**
 * POST Buenos dias (or custom) and poll `/lab/jobs/{id}` until terminal.
 * Fails fast on HTTP 500/502 from POST or any poll response.
 */
export async function postChatMessageAndPollTerminal(
  request: APIRequestContext,
  token: string,
  conversationId: string,
  content: string,
  options?: { pollTimeoutMs?: number },
): Promise<LabJobStatusBody> {
  const postRes = await request.post(productUrl(`/conversations/${conversationId}/messages`), {
    headers: { ...authHeaders(token), "Content-Type": "application/json" },
    data: { content, llmModel: null },
  });
  const postStatus = postRes.status();
  const postText = await postRes.text();
  assertBodyNotHtml(postText, "POST conversations/{id}/messages");
  expect(postStatus, postText).not.toBe(502);
  expect(postStatus, postText).not.toBe(500);
  expect(postStatus, postText).toBe(202);
  const accepted = JSON.parse(postText) as LabJobAccepted;
  expect(accepted.jobId).toBeTruthy();
  expect(accepted.pollPath).toContain("lab/jobs");
  expect(accepted.streamPath).toBeTruthy();

  const deadline = Date.now() + (options?.pollTimeoutMs ?? 180_000);
  for (;;) {
    const pollRes = await request.get(productUrl(`/lab/jobs/${accepted.jobId}`), {
      headers: authHeaders(token),
    });
    const pollStatus = pollRes.status();
    const pollText = await pollRes.text();
    assertBodyNotHtml(pollText, `GET lab/jobs/${accepted.jobId}`);
    expect(pollStatus, pollText).not.toBe(502);
    expect(pollStatus, pollText).not.toBe(500);
    expect(pollStatus, pollText).toBe(200);
    const body = JSON.parse(pollText) as LabJobStatusBody;
    expect(body.id).toBe(accepted.jobId);
    expect(typeof body.terminal).toBe("boolean");
    if (body.terminal) return body;
    if (Date.now() > deadline) {
      throw new Error(`pollLabJob timeout jobId=${accepted.jobId}`);
    }
    await new Promise((r) => setTimeout(r, 900));
  }
}

/** Creates a fresh project, activates it, and opens an empty conversation (API-only). */
export async function getChatPresetCatalog(
  request: APIRequestContext,
  token: string,
): Promise<ChatPresetCatalogDto> {
  const res = await request.get(productUrl("/chat/presets/catalog"), {
    headers: authHeaders(token),
  });
  const raw = await res.text();
  assertBodyNotHtml(raw, "GET chat/presets/catalog");
  expect(res.status(), raw).toBe(200);
  return JSON.parse(raw) as ChatPresetCatalogDto;
}

export function findDemoBestPresetId(catalog: ChatPresetCatalogDto): string {
  const match = catalog.productPresets?.find((p) => p.name === "Demo_Best");
  expect(match?.id, "Demo_Best preset in catalog").toBeTruthy();
  return match!.id;
}

export async function uploadProjectDocument(
  request: APIRequestContext,
  token: string,
  projectId: string,
  absolutePath: string,
  fileName: string,
): Promise<ProjectDocumentDto> {
  const res = await request.post(productUrl(`/projects/${projectId}/documents`), {
    headers: authHeaders(token),
    multipart: {
      file: {
        name: fileName,
        mimeType: fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : "text/plain",
        buffer: fs.readFileSync(absolutePath),
      },
    },
  });
  const raw = await res.text();
  assertBodyNotHtml(raw, `POST /projects/${projectId}/documents`);
  expect(res.status(), raw).toBe(201);
  return JSON.parse(raw) as ProjectDocumentDto;
}

export async function waitForProjectDocumentReady(
  request: APIRequestContext,
  token: string,
  projectId: string,
  fileName: string,
  timeoutMs = 180_000,
): Promise<ProjectDocumentDto> {
  const intervals = [200, 400, 800, 1200, 2000];
  let intervalIdx = 0;
  const deadline = Date.now() + timeoutMs;
  let readySince: number | undefined;

  for (;;) {
    const res = await request.get(productUrl(`/projects/${projectId}/documents`), {
      headers: authHeaders(token),
    });
    const raw = await res.text();
    assertBodyNotHtml(raw, `GET /projects/${projectId}/documents`);
    expect(res.status(), raw).toBe(200);
    const docs = JSON.parse(raw) as ProjectDocumentDto[];
    const doc = docs.find((d) => d.fileName === fileName);
    if (doc?.status === "READY") {
      if ((doc.chunkCount ?? 0) > 0) {
        return doc;
      }
      readySince ??= Date.now();
      if (Date.now() - readySince >= 3_000) {
        return doc;
      }
    } else {
      readySince = undefined;
    }
    if (doc?.status === "ERROR") {
      throw new Error(
        `Document ingest ERROR file=${fileName} id=${doc.id} ${doc.errorMessage ?? ""}`,
      );
    }
    if (Date.now() > deadline) {
      throw new Error(`Timed out waiting for READY document ${fileName} on project ${projectId}`);
    }
    const sleepMs = intervals[Math.min(intervalIdx, intervals.length - 1)];
    intervalIdx += 1;
    await new Promise((r) => setTimeout(r, sleepMs));
  }
}

/** Waits until the project active snapshot is ACTIVE with HYBRID materialization (Demo_Best). */
export async function waitForActiveHybridSnapshot(
  request: APIRequestContext,
  token: string,
  projectId: string,
  timeoutMs = 60_000,
): Promise<void> {
  const intervals = [200, 400, 800, 1200, 2000];
  let intervalIdx = 0;
  const deadline = Date.now() + timeoutMs;

  for (;;) {
    const res = await request.get(productUrl(`/projects/${projectId}/knowledge/snapshots/active`), {
      headers: authHeaders(token),
    });
    const raw = await res.text();
    assertBodyNotHtml(raw, `GET /projects/${projectId}/knowledge/snapshots/active`);
    if (res.status() === 200) {
      const body = JSON.parse(raw) as {
        status?: string;
        indexProfile?: { materializationStrategy?: string };
      };
      if (
        body.status === "ACTIVE" &&
        body.indexProfile?.materializationStrategy === "HYBRID"
      ) {
        return;
      }
    }
    if (Date.now() > deadline) {
      throw new Error(`Timed out waiting for HYBRID active snapshot on project ${projectId}`);
    }
    const sleepMs = intervals[Math.min(intervalIdx, intervals.length - 1)];
    intervalIdx += 1;
    await new Promise((r) => setTimeout(r, sleepMs));
  }
}

/** Rebuilds project knowledge index for a preset (e.g. Demo_Best HYBRID materialization). */
export async function rebuildProjectKnowledgeForPreset(
  request: APIRequestContext,
  token: string,
  projectId: string,
  presetId: string,
): Promise<void> {
  const res = await request.post(productUrl(`/projects/${projectId}/knowledge/rebuild/execute`), {
    headers: { ...authHeaders(token), "Content-Type": "application/json" },
    data: {
      corpusScope: "PROJECT_SHARED",
      presetId,
    },
  });
  const raw = await res.text();
  assertBodyNotHtml(raw, `POST /projects/${projectId}/knowledge/rebuild/execute`);
  expect(res.status(), raw).toBe(200);
  const body = JSON.parse(raw) as { asyncTaskId?: string | null };
  if (body.asyncTaskId) {
    const deadline = Date.now() + 180_000;
    for (;;) {
      const pollRes = await request.get(productUrl(`/lab/jobs/${body.asyncTaskId}`), {
        headers: authHeaders(token),
      });
      const pollText = await pollRes.text();
      assertBodyNotHtml(pollText, `GET lab/jobs/${body.asyncTaskId}`);
      expect(pollRes.status(), pollText).toBe(200);
      const job = JSON.parse(pollText) as LabJobStatusBody;
      if (job.terminal) {
        expect(job.status, pollText).toBe("SUCCEEDED");
        break;
      }
      if (Date.now() > deadline) {
        throw new Error(`rebuild async task timeout taskId=${body.asyncTaskId}`);
      }
      await new Promise((r) => setTimeout(r, 900));
    }
  }
  await waitForActiveHybridSnapshot(request, token, projectId);
  await new Promise((r) => setTimeout(r, 5_000));
}

/** Uploads acta fixtures so Demo_Best deterministic metadata routes have corpus evidence. */
export async function uploadActaAcceptanceCorpus(
  request: APIRequestContext,
  token: string,
  projectId: string,
  fileNames: readonly string[] = ACTA_ACCEPTANCE_MINIMAL_FILES,
): Promise<void> {
  for (const fileName of fileNames) {
    const absolutePath = path.join(ACTA_ACCEPTANCE_FIXTURES_DIR, fileName);
    expect(fs.existsSync(absolutePath), `missing acta fixture ${absolutePath}`).toBe(true);
    await uploadProjectDocument(request, token, projectId, absolutePath, fileName);
    await waitForProjectDocumentReady(request, token, projectId, fileName);
  }
}

export async function ensureDemoBestConversation(
  request: APIRequestContext,
  token: string,
  options?: { withActaCorpus?: boolean; actaFixtureFiles?: readonly string[] },
): Promise<{ projectId: string; conversationId: string; demoBestPresetId: string }> {
  const catalog = await getChatPresetCatalog(request, token);
  const demoBestPresetId = findDemoBestPresetId(catalog);
  const { projectId, conversationId } = await createActivatedProjectAndConversation(request, token);
  await patchConversation(request, token, conversationId, { presetId: demoBestPresetId });
  if (options?.withActaCorpus !== false) {
    await uploadActaAcceptanceCorpus(
      request,
      token,
      projectId,
      options?.actaFixtureFiles ?? ACTA_ACCEPTANCE_MINIMAL_FILES,
    );
    await rebuildProjectKnowledgeForPreset(request, token, projectId, demoBestPresetId);
  }
  return { projectId, conversationId, demoBestPresetId };
}

export async function getConversationMessages(
  request: APIRequestContext,
  token: string,
  conversationId: string,
): Promise<MessageDto[]> {
  const res = await request.get(productUrl(`/conversations/${conversationId}/messages`), {
    headers: authHeaders(token),
  });
  const raw = await res.text();
  assertBodyNotHtml(raw, `GET conversations/${conversationId}/messages`);
  expect(res.status(), raw).toBe(200);
  return JSON.parse(raw) as MessageDto[];
}

export async function postChatAndGetLatestAssistant(
  request: APIRequestContext,
  token: string,
  conversationId: string,
  content: string,
  options?: { pollTimeoutMs?: number },
): Promise<{ job: LabJobStatusBody; assistant: MessageDto | undefined }> {
  const job = await postChatMessageAndPollTerminal(request, token, conversationId, content, options);
  const messages = await getConversationMessages(request, token, conversationId);
  const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
  return { job, assistant };
}

/** PATCH `{product}/conversations/{id}` (subset of fields). */
export async function patchConversation(
  request: APIRequestContext,
  token: string,
  conversationId: string,
  body: { documentFilter?: string[]; title?: string; presetId?: string },
): Promise<void> {
  const res = await request.patch(productUrl(`/conversations/${conversationId}`), {
    headers: {
      ...authHeaders(token),
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    data: body,
  });
  const patchText = await res.text();
  assertBodyNotHtml(patchText, `PATCH conversations/${conversationId}`);
  expect(res.status(), patchText).toBe(200);
}

export async function createActivatedProjectAndConversation(
  request: APIRequestContext,
  token: string,
): Promise<{ projectId: string; conversationId: string }> {
  const name = `api-chat-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const pr = await request.post(productUrl("/projects"), {
    headers: { ...authHeaders(token), "Content-Type": "application/json" },
    data: { name },
  });
  expect(pr.status(), await pr.text()).toBe(201);
  const { id: projectId } = (await pr.json()) as { id: string };

  const act = await request.put(productUrl(`/projects/${projectId}/activate`), {
    headers: authHeaders(token),
  });
  expect(act.status(), await act.text()).toBe(200);

  const cr = await request.post(productUrl(`/projects/${projectId}/conversations`), {
    headers: { ...authHeaders(token), "Content-Type": "application/json" },
    data: {},
  });
  expect(cr.status(), await cr.text()).toBe(201);
  const conv = (await cr.json()) as { id: string };
  expect(conv.id).toBeTruthy();
  return { projectId, conversationId: conv.id };
}
