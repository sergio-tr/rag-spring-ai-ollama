import { expect, type APIRequestContext } from "@playwright/test";
import { authHeaders } from "./auth";
import { productUrl } from "./env";

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
};

export type ConversationDto = {
  id: string;
  presetId?: string | null;
  effectivePresetId?: string | null;
  documentFilter?: string[];
};

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
