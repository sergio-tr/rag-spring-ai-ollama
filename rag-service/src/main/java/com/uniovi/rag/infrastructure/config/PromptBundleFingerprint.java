package com.uniovi.rag.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.application.service.evaluation.EvaluationJudgePromptSources;
import com.uniovi.rag.application.service.evaluation.baseline.EvaluationBaselinePrompts;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import com.uniovi.rag.application.service.runtime.factual.FactualRevisionPrompts;
import com.uniovi.rag.application.service.runtime.judge.RuntimeJudgePromptSources;
import com.uniovi.rag.application.service.runtime.memory.ConversationCondensePromptSources;
import com.uniovi.rag.application.service.runtime.query.QueryRewritePromptSources;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable SHA-256 fingerprint over frozen and runtime prompt sources for evaluation reproducibility.
 * Exports hashes and group ids only — never full prompt text.
 */
public final class PromptBundleFingerprint {

    /** Bump when group inventory or canonical serialization changes. */
    public static final String BUNDLE_VERSION = "wave2-prompt-bundle-v1";

    public static final String GROUP_RUNTIME_ANSWER_PROMPTS = "RUNTIME_ANSWER_PROMPTS";
    public static final String GROUP_EVALUATION_JUDGE = "EVALUATION_JUDGE";
    public static final String GROUP_RUNTIME_JUDGE = "RUNTIME_JUDGE";
    public static final String GROUP_QUERY_REWRITE = "QUERY_REWRITE";
    public static final String GROUP_CONVERSATION_CONDENSE = "CONVERSATION_CONDENSE";
    public static final String GROUP_FACTUAL_REVISION = "FACTUAL_REVISION";
    public static final String GROUP_CLARIFICATION_TEMPLATES = "CLARIFICATION_TEMPLATES";
    public static final String GROUP_EVAL_BASELINE = "EVAL_BASELINE";
    public static final String GROUP_METADATA_INGEST = "METADATA_INGEST";
    public static final String GROUP_SYSTEM_PROMPT_LAYERS = "SYSTEM_PROMPT_LAYERS";
    public static final String GROUP_LLM_SYSTEM_PROMPT = "LLM_SYSTEM_PROMPT";

    public static final String GROUP_FUNCTION_CALLING = "FUNCTION_CALLING_USER_ASSEMBLY";
    public static final String GROUP_FACTUAL_VERIFIER = "FACTUAL_VERIFIER_RULES";
    public static final String GROUP_RETRIEVAL_CONTEXT = "RETRIEVAL_CONTEXT_ASSEMBLY";

    private static final ObjectMapper CANONICAL_JSON =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private PromptBundleFingerprint() {}

    public record GroupHash(String groupId, String sha256Hex) {}

    public record ExcludedGroup(String groupId, String reason) {}

    public record Result(
            String bundleVersion,
            String bundleHashSha256,
            List<GroupHash> includedGroups,
            List<ExcludedGroup> excludedGroups) {

        public Map<String, Object> toProvenanceMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("promptBundleVersion", bundleVersion);
            out.put("promptBundleSha256", bundleHashSha256);
            List<Map<String, String>> included = new ArrayList<>();
            for (GroupHash g : includedGroups) {
                included.add(Map.of("groupId", g.groupId(), "hash", g.sha256Hex()));
            }
            out.put("promptBundleIncludedGroups", List.copyOf(included));
            List<Map<String, String>> excluded = new ArrayList<>();
            for (ExcludedGroup g : excludedGroups) {
                excluded.add(Map.of("groupId", g.groupId(), "reason", g.reason()));
            }
            out.put("promptBundleExcludedGroups", List.copyOf(excluded));
            return Map.copyOf(out);
        }
    }

    /** Fingerprints all code-frozen prompt sources (no per-run DB or user text). */
    public static Result computeFrozen() {
        return compute(null, null, null, null);
    }

    /**
     * Frozen sources plus optional runtime overlays (system layers, llmSystemPrompt, evaluation snapshot).
     */
    public static Result computeForRuntime(SystemPromptLayers layers, String effectiveSystemPrompt, String llmSystemPrompt) {
        return compute(layers, effectiveSystemPrompt, llmSystemPrompt, null);
    }

    public static Result computeForEvaluation(PromptProfileSnapshot prompts, String llmSystemPrompt) {
        return compute(null, null, llmSystemPrompt, prompts);
    }

    private static Result compute(
            SystemPromptLayers layers,
            String effectiveSystemPrompt,
            String llmSystemPrompt,
            PromptProfileSnapshot prompts) {
        List<GroupHash> included = new ArrayList<>();
        included.add(group(GROUP_RUNTIME_ANSWER_PROMPTS, RuntimeAnswerPrompts.fingerprintMaterial()));
        included.add(group(GROUP_EVALUATION_JUDGE, EvaluationJudgePromptSources.fingerprintMaterial()));
        included.add(group(GROUP_RUNTIME_JUDGE, RuntimeJudgePromptSources.fingerprintMaterial()));
        included.add(group(GROUP_QUERY_REWRITE, QueryRewritePromptSources.fingerprintMaterial()));
        included.add(group(GROUP_CONVERSATION_CONDENSE, ConversationCondensePromptSources.fingerprintMaterial()));
        included.add(group(GROUP_FACTUAL_REVISION, FactualRevisionPrompts.fingerprintMaterial()));
        included.add(group(GROUP_CLARIFICATION_TEMPLATES, ClarificationQuestionKind.fingerprintMaterial()));
        included.add(group(GROUP_EVAL_BASELINE, evalBaselineMaterial()));
        included.add(group(GROUP_METADATA_INGEST, metadataIngestMaterial()));

        if (layers != null || (effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank())) {
            included.add(
                    group(
                            GROUP_SYSTEM_PROMPT_LAYERS,
                            systemLayersMaterial(layers, effectiveSystemPrompt, prompts)));
        } else if (prompts != null) {
            included.add(
                    group(
                            GROUP_SYSTEM_PROMPT_LAYERS,
                            systemLayersMaterial(SystemPromptLayers.empty(), prompts.effectiveSystemPrompt(), prompts)));
        }

        if (llmSystemPrompt != null && !llmSystemPrompt.isBlank()) {
            included.add(group(GROUP_LLM_SYSTEM_PROMPT, llmSystemPrompt.trim()));
        }

        List<ExcludedGroup> excluded = defaultExcludedGroups();
        if (llmSystemPrompt == null || llmSystemPrompt.isBlank()) {
            excluded.add(
                    new ExcludedGroup(
                            GROUP_LLM_SYSTEM_PROMPT,
                            "llmSystemPrompt not resolved for this fingerprint scope"));
        } else {
            excluded.removeIf(g -> GROUP_LLM_SYSTEM_PROMPT.equals(g.groupId()));
        }

        String bundleHash = bundleHashSha256(included);
        return new Result(BUNDLE_VERSION, bundleHash, List.copyOf(included), List.copyOf(excluded));
    }

    private static List<ExcludedGroup> defaultExcludedGroups() {
        List<ExcludedGroup> excluded = new ArrayList<>();
        excluded.add(
                new ExcludedGroup(
                        GROUP_FUNCTION_CALLING,
                        "Dynamic user message assembly from QueryPlan; no fixed system prompt"));
        excluded.add(
                new ExcludedGroup(
                        GROUP_FACTUAL_VERIFIER,
                        "Rule-based factual verifier heuristics; not LLM prompt text"));
        excluded.add(
                new ExcludedGroup(
                        GROUP_RETRIEVAL_CONTEXT,
                        "Retrieved chunk context is assembled per query; not a frozen prompt template"));
        return excluded;
    }

    private static String evalBaselineMaterial() {
        return String.join(
                "\n---\n",
                EvaluationBaselinePrompts.PROFILE_VERSION,
                EvaluationBaselinePrompts.BASE_SYSTEM,
                EvaluationBaselinePrompts.PROJECT_SYSTEM,
                EvaluationBaselinePrompts.CHAT_SYSTEM,
                EvaluationBaselinePrompts.RETRIEVAL_QUESTION_TEMPLATE,
                EvaluationBaselinePrompts.ANSWER_FORMATTING);
    }

    private static String metadataIngestMaterial() {
        return String.join(
                "\n---\n",
                MetadataMinuteDocumentService.SYSTEM_PROMPT_LINE_DATA,
                MetadataMinuteDocumentService.SYSTEM_PROMPT_SUMMARY);
    }

    private static String systemLayersMaterial(
            SystemPromptLayers layers, String effectiveSystemPrompt, PromptProfileSnapshot prompts) {
        StringBuilder sb = new StringBuilder();
        if (layers != null) {
            sb.append("base=").append(nullToEmpty(layers.base())).append('\n');
            sb.append("account=").append(nullToEmpty(layers.account())).append('\n');
            sb.append("project=").append(nullToEmpty(layers.project())).append('\n');
            sb.append("presetWorkflow=").append(nullToEmpty(layers.presetWorkflow())).append('\n');
        }
        if (prompts != null) {
            sb.append("snapshotVersion=").append(nullToEmpty(prompts.profileVersion())).append('\n');
            sb.append("snapshotBaseSha=").append(sha256Hex(nullToEmpty(prompts.baseSystem()))).append('\n');
            sb.append("snapshotProjectSha=").append(sha256Hex(nullToEmpty(prompts.projectSystem()))).append('\n');
            sb.append("snapshotChatSha=").append(sha256Hex(nullToEmpty(prompts.chatSystem()))).append('\n');
            sb.append("snapshotEffectiveSha=").append(nullToEmpty(prompts.effectiveSystemPromptSha256())).append('\n');
        }
        sb.append("effective=").append(nullToEmpty(effectiveSystemPrompt));
        return sb.toString();
    }

    private static GroupHash group(String groupId, String material) {
        return new GroupHash(groupId, sha256Hex(material));
    }

    private static String bundleHashSha256(List<GroupHash> included) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            for (GroupHash g : included) {
                body.put(g.groupId(), g.sha256Hex());
            }
            byte[] utf8 = CANONICAL_JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return sha256HexBytes(MessageDigest.getInstance("SHA-256").digest(utf8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash prompt bundle", e);
        }
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = input != null ? input.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return sha256HexBytes(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String sha256HexBytes(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
