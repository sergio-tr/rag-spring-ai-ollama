import { execSync } from "node:child_process";
import * as fs from "node:fs";
import * as path from "node:path";
import { expect, type APIRequestContext } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "./auth";
import { parseJsonExpectNonHtml } from "./json-contract";
import { actuatorHealthUrl, integrationCredentials, productUrl } from "./env";
import {
  ACTA_ACCEPTANCE_MINIMAL_FILES,
  ensureDemoBestConversation,
  postChatAndGetLatestAssistant,
} from "./chat-runtime-api";
import { OLLAMA_ERROR_RE, PROVIDER_MISMATCH_RE } from "./e2e-multiturn-assertions";

export const PROVIDER_EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "../../../../docs/evidence/sprint-s4-fullstack-runtime-closure-20260628/06_provider_runtime_acceptance",
);

export const PHASE_C_LIVE_SMOKE_EVIDENCE_DIR = path.resolve(
  process.cwd(),
  "..",
  "../../../../docs/evidence/phase-c-gateway-parity-live-smoke-20260628",
);

export type ReadinessRagProvider = {
  status: string;
  details?: {
    chatProvider?: string;
    embeddingProvider?: string;
    openAiChat?: string;
    openAiEmbeddings?: string;
    ollama?: Record<string, unknown>;
    failures?: string[];
  };
};

export type ProviderCheck = {
  id: string;
  description: string;
  pass: boolean;
  evidence: string;
};

export type ProviderAcceptanceResult = {
  providerMode: "OPENAI_COMPATIBLE" | "OLLAMA_NATIVE" | "SKIPPED";
  generatedAt: string;
  conversationId?: string;
  checks: ProviderCheck[];
  allPass: boolean;
  logSample?: string;
  unitTestSummary?: string;
};

const BACKEND_CONTAINER = process.env.E2E_BACKEND_CONTAINER ?? "docker-backend-dev-1";

export async function fetchReadinessRagProvider(
  request: APIRequestContext,
): Promise<ReadinessRagProvider> {
  const res = await request.get(actuatorHealthUrl("/readiness"));
  const raw = await res.text();
  expect(res.status(), raw).toBe(200);
  const body = JSON.parse(raw) as {
    components?: { ragProvider?: ReadinessRagProvider };
  };
  const rag = body.components?.ragProvider;
  expect(rag, "readiness.ragProvider missing").toBeTruthy();
  return rag!;
}

export function scrapeBackendLogsSince(sinceIso: string): string {
  try {
    return execSync(`docker logs ${BACKEND_CONTAINER} --since "${sinceIso}" 2>&1`, {
      encoding: "utf8",
      maxBuffer: 8 * 1024 * 1024,
    });
  } catch (e) {
    const err = e as { stdout?: string; stderr?: string; message?: string };
    return [err.stdout ?? "", err.stderr ?? "", err.message ?? ""].join("\n");
  }
}

export function analyzeOpenAiCompatibleLogs(logs: string): {
  chatOpenAiOps: number;
  embedHints: number;
  ollamaPortHits: number;
  v1ChatHits: number;
  v1EmbedHits: number;
  apiTagsHits: number;
} {
  const llmLines = logs
    .split("\n")
    .filter((l) => /LLM operation|Secondary LLM|openAiChat|openAiEmbeddings|\/v1\//i.test(l));
  const ollamaPortHits = llmLines.filter((l) => /:11434/.test(l)).length;
  const v1ChatHits = logs.split("\n").filter((l) => /\/v1\/chat\/completions/i.test(l)).length;
  const v1EmbedHits = logs.split("\n").filter((l) => /\/v1\/embeddings/i.test(l)).length;
  const apiTagsHits = logs.split("\n").filter((l) => /\/api\/tags/i.test(l)).length;
  const chatOpenAiOps = logs
    .split("\n")
    .filter((l) => /operation=chat provider=OPENAI_COMPATIBLE/i.test(l)).length;
  const embedHints = logs.split("\n").filter((l) => /embeddingProvider=OPENAI_COMPATIBLE/i.test(l)).length;
  return { chatOpenAiOps, embedHints, ollamaPortHits, v1ChatHits, v1EmbedHits, apiTagsHits };
}

/** Count DEBUG secondary LLM operations in backend logs (e.g. query-rewrite, conversation-condense). */
export function countSecondaryOps(logs: string, operation: string): number {
  const op = operation.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`operation=${op}\\b`, "i");
  return logs.split("\n").filter((l) => pattern.test(l)).length;
}

export function countSecondaryOpsOpenAi(logs: string, operation: string): number {
  const op = operation.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`operation=${op}.*provider=OPENAI_COMPATIBLE`, "i");
  return logs.split("\n").filter((l) => pattern.test(l)).length;
}

export function forbiddenProviderPatterns(logs: string): Record<string, number> {
  const llmLines = logs
    .split("\n")
    .filter((l) => /LLM operation|Secondary LLM|openAiChat|openAiEmbeddings|\/v1\//i.test(l));
  const secondaryLines = logs
    .split("\n")
    .filter((l) => /Secondary LLM|operation=query-rewrite|operation=conversation-condense/i.test(l));
  const scan = (lines: string[]) => ({
    ollamaPort11434: lines.filter((l) => /:11434/.test(l)).length,
    ollamaOptions: lines.filter((l) => /OllamaOptions/i.test(l)).length,
    providerMismatch: lines.filter((l) => /provider mismatch/i.test(l)).length,
    malformedApiKey: lines.filter((l) => /Malformed API Key/i.test(l)).length,
    fallbackToOllama: lines.filter((l) => /fallback to Ollama/i.test(l)).length,
  });
  const allLines = logs.split("\n");
  const allScan = scan(allLines);
  return {
    ...allScan,
    secondaryLlmScoped: scan(secondaryLines).ollamaPort11434,
    llmWindowScoped: scan(llmLines).ollamaPort11434,
  };
}

export function runProviderUnitTests(): { summary: string; checks: ProviderCheck[] } {
  const suites: Array<{ id: string; description: string; test: string }> = [
    {
      id: "http-routing",
      description: "Chat uses /v1/chat/completions; embeddings use /v1/embeddings (ProviderHttpClientIntegrationTest)",
      test: "ProviderHttpClientIntegrationTest",
    },
    {
      id: "ner-provider",
      description: "NER routes via provider-aware secondary executor (MinuteNERQueryAnalyserProviderAwareTest)",
      test: "MinuteNERQueryAnalyserProviderAwareTest",
    },
    {
      id: "metadata-cache-provider",
      description: "Metadata LLM cache uses OPENAI_COMPATIBLE client when configured",
      test: "MetadataLlmResponseCacheServiceTest",
    },
    {
      id: "judge-provider",
      description: "Evaluation judge uses configured provider (EvaluationJudgeLlmExecutorTest)",
      test: "EvaluationJudgeLlmExecutorTest",
    },
    {
      id: "error-composer-provider",
      description: "Error composer avoids Ollama wording under OPENAI_COMPATIBLE",
      test: "LlmErrorComposerTest",
    },
    {
      id: "health-probes",
      description: "RagProvider health indicator probes /v1/* for OpenAI stack",
      test: "RagProviderHealthIndicatorTest",
    },
  ];
  const ragServiceDir = path.resolve(process.cwd(), "..", "rag-service");
  const checks: ProviderCheck[] = [];
  const summaries: string[] = [];
  for (const suite of suites) {
    try {
      const out = execSync(`cd "${ragServiceDir}" && mvn -q test -Dtest=${suite.test} 2>&1`, {
        encoding: "utf8",
        maxBuffer: 2 * 1024 * 1024,
        timeout: 180_000,
      });
      summaries.push(`${suite.test}: OK`);
      checks.push({
        id: suite.id,
        description: suite.description,
        pass: true,
        evidence: (out.trim() || "OK").slice(-200),
      });
    } catch (e) {
      const err = e as { stdout?: string; stderr?: string };
      const msg = [err.stdout ?? "", err.stderr ?? "FAILED"].join("\n").slice(-500);
      summaries.push(`${suite.test}: FAIL`);
      checks.push({
        id: suite.id,
        description: suite.description,
        pass: false,
        evidence: msg,
      });
    }
  }
  return { summary: summaries.join("\n"), checks };
}

export function isOllamaReachable(): boolean {
  const urls = [
    process.env.OLLAMA_PROBE_URL,
    "http://127.0.0.1:11434/api/tags",
    "http://host.docker.internal:11434/api/tags",
  ].filter(Boolean) as string[];
  for (const url of urls) {
    try {
      execSync(`curl -sf --max-time 3 "${url}"`, { encoding: "utf8", stdio: "pipe" });
      return true;
    } catch {
      /* try next */
    }
  }
  return false;
}

export async function runOpenAiCompatibleLiveChecks(
  request: APIRequestContext,
): Promise<ProviderAcceptanceResult> {
  const generatedAt = new Date().toISOString();
  const sinceIso = generatedAt;
  const checks: ProviderCheck[] = [];
  const { email, password } = integrationCredentials();
  const token = await loginAndGetToken(request, email, password);

  const readiness = await fetchReadinessRagProvider(request);
  const chatProvider = readiness.details?.chatProvider ?? "";
  const embeddingProvider = readiness.details?.embeddingProvider ?? "";
  checks.push({
    id: "readiness-chat-provider",
    description: "readiness.ragProvider.chatProvider=OPENAI_COMPATIBLE",
    pass: chatProvider === "OPENAI_COMPATIBLE",
    evidence: `chatProvider=${chatProvider}`,
  });
  checks.push({
    id: "readiness-embedding-provider",
    description: "readiness.ragProvider.embeddingProvider=OPENAI_COMPATIBLE",
    pass: embeddingProvider === "OPENAI_COMPATIBLE",
    evidence: `embeddingProvider=${embeddingProvider}`,
  });
  checks.push({
    id: "readiness-openai-chat-probe",
    description: "Health probe uses /v1/chat/completions (openAiChat=up)",
    pass: readiness.details?.openAiChat === "up",
    evidence: `openAiChat=${readiness.details?.openAiChat ?? "missing"}`,
  });
  checks.push({
    id: "readiness-openai-embed-probe",
    description: "Health probe uses /v1/embeddings (openAiEmbeddings=up)",
    pass: readiness.details?.openAiEmbeddings === "up",
    evidence: `openAiEmbeddings=${readiness.details?.openAiEmbeddings ?? "missing"}`,
  });

  const selectableRes = await request.get(
    productUrl("/me/llm/selectable-models?capability=CHAT"),
    { headers: authHeaders(token) },
  );
  const selectableBody = parseJsonExpectNonHtml(
    await selectableRes.text(),
    "GET /me/llm/selectable-models",
  ) as {
    effectiveProvider?: string;
    provider?: string;
    models: { modelName: string; selectable: boolean }[];
  };
  const selectorProvider =
    selectableBody.effectiveProvider ?? selectableBody.provider ?? "";
  checks.push({
    id: "model-selector-provider-scoped",
    description: "Model selector catalog scoped to OPENAI_COMPATIBLE (not /api/tags)",
    pass:
      selectableRes.status() === 200 &&
      selectorProvider === "OPENAI_COMPATIBLE" &&
      selectableBody.models.some((m) => m.selectable),
    evidence: `effectiveProvider=${selectorProvider} models=${selectableBody.models.length}`,
  });

  const { conversationId } = await ensureDemoBestConversation(request, token, {
    actaFixtureFiles: ACTA_ACCEPTANCE_MINIMAL_FILES,
  });
  const probeQuery = "quienes fueron los asistentes del acta del 24 de febrero de 2025";
  const { job, assistant } = await postChatAndGetLatestAssistant(
    request,
    token,
    conversationId,
    probeQuery,
    { pollTimeoutMs: 300_000 },
  );
  const answer = (assistant?.content ?? "").trim();
  checks.push({
    id: "chat-job-succeeded",
    description: "Chat turn completes under OPENAI_COMPATIBLE runtime",
    pass: job.status === "SUCCEEDED" && answer.length > 20,
    evidence: `job=${job.status} answerLen=${answer.length}`,
  });
  checks.push({
    id: "no-ollama-error-in-answer",
    description: "User-facing answer does not mention Ollama errors",
    pass: !OLLAMA_ERROR_RE.test(answer),
    evidence: answer.slice(0, 160),
  });
  checks.push({
    id: "no-provider-mismatch-in-answer",
    description: "User-facing answer has no provider mismatch leak",
    pass: !PROVIDER_MISMATCH_RE.test(answer),
    evidence: "pattern scan on final answer",
  });

  const logs = scrapeBackendLogsSince(sinceIso);
  const logStats = analyzeOpenAiCompatibleLogs(logs);
  checks.push({
    id: "chat-uses-openai-provider-logs",
    description: "Backend logs show chat operation with provider=OPENAI_COMPATIBLE",
    pass: logStats.chatOpenAiOps > 0 || logStats.v1ChatHits > 0,
    evidence: `chatOpenAiOps=${logStats.chatOpenAiOps} v1ChatHits=${logStats.v1ChatHits}`,
  });
  checks.push({
    id: "no-11434-in-llm-window",
    description: "No :11434 hits in LLM log window during stable chat",
    pass: logStats.ollamaPortHits === 0,
    evidence: `ollamaPortHits=${logStats.ollamaPortHits}`,
  });
  checks.push({
    id: "no-api-tags-in-llm-window",
    description: "Model selector path does not call Ollama /api/tags during acceptance window",
    pass: logStats.apiTagsHits === 0,
    evidence: `apiTagsHits=${logStats.apiTagsHits}`,
  });

  const unitRollup = runProviderUnitTests();
  checks.push(...unitRollup.checks);

  const allPass = checks.every((c) => c.pass);
  return {
    providerMode: "OPENAI_COMPATIBLE",
    generatedAt,
    conversationId,
    checks,
    allPass,
    logSample: logs.split("\n").slice(-40).join("\n"),
    unitTestSummary: unitRollup.summary,
  };
}

export function buildOllamaNativeSkippedResult(reason: string): ProviderAcceptanceResult {
  const unitRollup = runProviderUnitTests();
  return {
    providerMode: "SKIPPED",
    generatedAt: new Date().toISOString(),
    checks: [
      {
        id: "ollama-live-stack",
        description: "Live OLLAMA_NATIVE stack run (/api/chat, /api/embed, no /v1/*)",
        pass: false,
        evidence: reason,
      },
      ...unitRollup.checks.filter((c) => c.id === "http-routing"),
    ],
    allPass: false,
    unitTestSummary: unitRollup.summary,
  };
}

function yn(pass: boolean): string {
  return pass ? "PASS" : "FAIL";
}

export function writeProviderAcceptanceMd(
  filename: "OPENAI_COMPATIBLE_ACCEPTANCE.md" | "OLLAMA_NATIVE_ACCEPTANCE.md",
  title: string,
  result: ProviderAcceptanceResult,
): void {
  fs.mkdirSync(PROVIDER_EVIDENCE_DIR, { recursive: true });
  const lines = [
    `# ${title}`,
    "",
    `**Generated:** ${result.generatedAt}`,
    `**Mode:** ${result.providerMode}`,
    result.conversationId ? `**Conversation:** \`${result.conversationId}\`` : "",
    `**Overall:** ${result.allPass ? "PASS" : result.providerMode === "SKIPPED" ? "SKIPPED" : "FAIL"}`,
    "",
    "## Checks",
    "",
    "| ID | Check | Result | Evidence |",
    "|----|-------|--------|----------|",
    ...result.checks.map(
      (c) => `| ${c.id} | ${c.description} | ${yn(c.pass)} | ${c.evidence.replace(/\|/g, "\\|").slice(0, 120)} |`,
    ),
    "",
  ];
  if (result.logSample) {
    lines.push("## Log sample (tail)", "", "```text", result.logSample, "```", "");
  }
  if (result.unitTestSummary) {
    lines.push("## Unit test roll-up", "", "```text", result.unitTestSummary.slice(-1500), "```", "");
  }
  lines.push("FIN");
  fs.writeFileSync(path.join(PROVIDER_EVIDENCE_DIR, filename), lines.filter(Boolean).join("\n"));
}
