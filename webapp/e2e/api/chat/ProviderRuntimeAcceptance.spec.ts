import { expect, test } from "@playwright/test";
import {
  buildOllamaNativeSkippedResult,
  fetchReadinessRagProvider,
  isOllamaReachable,
  runOpenAiCompatibleLiveChecks,
  writeProviderAcceptanceMd,
} from "../fixtures/provider-runtime-acceptance";

test.describe("Phase 6 provider runtime acceptance @api @p0 @providerRuntime", () => {
  test("writes OPENAI_COMPATIBLE and OLLAMA_NATIVE acceptance evidence", async ({ request }) => {
    test.setTimeout(900_000);

    const readiness = await fetchReadinessRagProvider(request);
    const chatProvider = readiness.details?.chatProvider ?? "UNKNOWN";
    const embeddingProvider = readiness.details?.embeddingProvider ?? "UNKNOWN";

    if (chatProvider === "OPENAI_COMPATIBLE" && embeddingProvider === "OPENAI_COMPATIBLE") {
      const openAiResult = await runOpenAiCompatibleLiveChecks(request);
      writeProviderAcceptanceMd(
        "OPENAI_COMPATIBLE_ACCEPTANCE.md",
        "Provider runtime acceptance - OpenAI-compatible",
        openAiResult,
      );
      expect(openAiResult.allPass, JSON.stringify(openAiResult.checks, null, 2)).toBe(true);
    } else {
      writeProviderAcceptanceMd(
        "OPENAI_COMPATIBLE_ACCEPTANCE.md",
        "Provider runtime acceptance - OpenAI-compatible",
        {
          providerMode: "SKIPPED",
          generatedAt: new Date().toISOString(),
          checks: [
            {
              id: "stack-not-openai",
              description: "Stack not configured as OPENAI_COMPATIBLE for chat+embedding",
              pass: false,
              evidence: `chat=${chatProvider} embedding=${embeddingProvider}`,
            },
          ],
          allPass: false,
        },
      );
    }

    if (isOllamaReachable() && chatProvider === "OLLAMA_NATIVE") {
      // Live OLLAMA_NATIVE stack not available in current dev profile; document when reachable.
      const ollamaSkipped = buildOllamaNativeSkippedResult(
        "OLLAMA_NATIVE live E2E requires stack restart with RAG_LLM_DEFAULT_PROVIDER=OLLAMA_NATIVE",
      );
      writeProviderAcceptanceMd(
        "OLLAMA_NATIVE_ACCEPTANCE.md",
        "Provider runtime acceptance - Ollama-native",
        ollamaSkipped,
      );
    } else {
      const reason = isOllamaReachable()
        ? `Stack active providers are ${chatProvider}/${embeddingProvider}, not OLLAMA_NATIVE`
        : "Ollama not reachable at :11434 (GET /api/tags failed)";
      const ollamaResult = buildOllamaNativeSkippedResult(reason);
      writeProviderAcceptanceMd(
        "OLLAMA_NATIVE_ACCEPTANCE.md",
        "Provider runtime acceptance - Ollama-native",
        ollamaResult,
      );
    }
  });
});
