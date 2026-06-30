#!/usr/bin/env node
/**
 * Runtime trace: 4-turn conversation with execution metadata capture.
 * Usage: node docker/scripts/runtime-trace.mjs
 */
import https from "node:https";
import fs from "node:fs";
import path from "node:path";

const BASE = (process.env.E2E_PRODUCT_URL ?? "https://127.0.0.1:8444").replace(/\/$/, "");
const API_PREFIX = (process.env.RAG_API_PRODUCT_BASE_PATH ?? "/api/v5").replace(/\/$/, "");
const productUrl = (p) => `${BASE}${API_PREFIX}${p.startsWith("/") ? p : `/${p}`}`;
const EMAIL = process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test";
const PASSWORD = process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev";
const DEMO_BEST = "cafe0001-0001-4001-8001-000000000003";
const ACTA_DIR = path.join(process.cwd(), "rag-service/src/main/resources/docs");
const ACTA_FILES = ["ACTA 5.pdf"];

const agent = new https.Agent({ rejectUnauthorized: false });
process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

async function req(method, urlPath, { token, body, form } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (body) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  if (form) {
    payload = form;
  }
  const res = await fetch(productUrl(urlPath), {
    method,
    headers,
    body: payload,
    // @ts-ignore
    agent,
  });
  const text = await res.text();
  if (text.startsWith("<")) throw new Error(`${method} ${urlPath} returned HTML (${res.status})`);
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${urlPath} ${res.status}: ${text.slice(0, 400)}`);
  return json;
}

async function login() {
  const data = await req("POST", "/auth/login", { body: { email: EMAIL, password: PASSWORD } });
  return data.accessToken;
}

async function pollJob(token, jobId, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const job = await req("GET", `/lab/jobs/${jobId}`, { token });
    if (job.terminal) return job;
    if (Date.now() > deadline) throw new Error(`Job ${jobId} timed out`);
    await new Promise((r) => setTimeout(r, 1500));
  }
}

async function postChat(token, conversationId, content) {
  const accepted = await req("POST", `/conversations/${conversationId}/messages`, {
    token,
    body: { content, llmModel: null },
  });
  const job = await pollJob(token, accepted.jobId);
  const messages = await req("GET", `/conversations/${conversationId}/messages`, { token });
  const user = [...messages].reverse().find((m) => m.role === "USER" && m.content === content);
  const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
  return { job, user, assistant, messages };
}

async function setupConversation(token) {
  const project = await req("POST", "/projects", {
    token,
    body: { name: `runtime-trace-${Date.now()}` },
  });
  await req("PUT", `/projects/${project.id}/activate`, { token });
  const conv = await req("POST", `/projects/${project.id}/conversations`, { token, body: {} });
  await req("PATCH", `/conversations/${conv.id}`, { token, body: { presetId: DEMO_BEST } });

  for (const file of ACTA_FILES) {
    const buf = fs.readFileSync(path.join(ACTA_DIR, file));
    const boundary = `----runtime${Date.now()}`;
    const head = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${file}"\r\nContent-Type: application/pdf\r\n\r\n`;
    const tail = `\r\n--${boundary}--\r\n`;
    const form = Buffer.concat([Buffer.from(head), buf, Buffer.from(tail)]);
    await fetch(productUrl(`/projects/${project.id}/documents`), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": `multipart/form-data; boundary=${boundary}`,
      },
      body: form,
      // @ts-ignore
      agent,
    });
  }
  await req("POST", `/projects/${project.id}/knowledge/rebuild`, {
    token,
    body: { presetId: DEMO_BEST },
  });
  return { projectId: project.id, conversationId: conv.id };
}

function pickMeta(assistant) {
  const m = assistant?.executionMetadata ?? {};
  return {
    anchoredActaDate: m.anchoredActaDate ?? m.anchored_acta_date ?? null,
    lastReferencedDate: m.lastReferencedDate ?? m.last_referenced_date ?? null,
    effectivePlanningInputText: m.effectivePlanningInputText ?? m.effective_planning_input_text ?? null,
    pendingClarification: m.pendingClarification ?? m.pending_clarification ?? null,
    selectedRoute: m.selectedRoute ?? m.selected_route ?? m.adaptiveRouteKind ?? null,
    guardDecision: m.guardDecision ?? m.recallGuardDecision ?? m.conversationRecallGuard ?? null,
    memoryOutcome: m.memoryOutcome ?? m.conversationMemoryOutcome ?? null,
    rawKeys: Object.keys(m),
  };
}

const TURNS = [
  "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
  "¿quién fue el presidente?",
  "y quién fue la secretaria?",
  "¿a qué hora empezó y a qué hora terminó esa acta?",
];

async function main() {
  const token = await login();
  const { conversationId } = await setupConversation(token);
  const trace = { conversationId, turns: [] };

  for (const q of TURNS) {
    const turn = await postChat(token, conversationId, q);
    trace.turns.push({
      question: q,
      userMessageId: turn.user?.id ?? null,
      assistantMessageId: turn.assistant?.id ?? null,
      jobStatus: turn.job.status,
      assistantPreview: (turn.assistant?.content ?? "").slice(0, 500),
      executionMetadata: pickMeta(turn.assistant),
      jobResultKeys: turn.job.result ? Object.keys(turn.job.result) : [],
    });
    process.stderr.write(`OK turn: ${q.slice(0, 40)}…\n`);
  }

  console.log(JSON.stringify(trace, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
