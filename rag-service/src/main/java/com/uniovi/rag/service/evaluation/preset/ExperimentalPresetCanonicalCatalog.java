package com.uniovi.rag.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical thesis experimental presets P0–P14.
 *
 * <p>This is the single source of truth for:
 * <ul>
 *   <li>seeded {@code rag_preset.values} (Chat execution)</li>
 *   <li>Lab experimental preset catalog (capabilities/support)</li>
 *   <li>Lab RAG preset benchmark terminal runtime overrides</li>
 * </ul>
 *
 * <p>Presets are cumulative by design: each preset defines its parent and a runtime delta map.
 * The effective runtime config for a code is {@code parentEffective + delta}.
 */
public final class ExperimentalPresetCanonicalCatalog {

    private ExperimentalPresetCanonicalCatalog() {}

    public enum RequiredMaterialization {
        NONE,
        DOCUMENT_LEVEL,
        CHUNK_LEVEL,
        HYBRID
    }

    public record IndexRequirements(
            RequiredMaterialization requiredMaterialization,
            boolean requiresMetadataSupport
    ) {
        public static IndexRequirements none() {
            return new IndexRequirements(RequiredMaterialization.NONE, false);
        }
    }

    public record CanonicalPreset(
            RagExperimentalPresetCode code,
            UUID productPresetId,
            RagExperimentalPresetCode parent,
            Map<String, Object> runtimeDelta,
            IndexRequirements indexRequirementsDelta,
            boolean requiresMultiTurn
    ) {}

    private static final EnumMap<RagExperimentalPresetCode, CanonicalPreset> PRESETS = new EnumMap<>(RagExperimentalPresetCode.class);

    static {
        // IMPORTANT: This ladder is intentionally cumulative and matches the thesis protocol in the repo.
        define(
                RagExperimentalPresetCode.P0,
                uuid("cafe0001-0001-4001-8001-000000000010"),
                null,
                Map.ofEntries(
                        Map.entry("useRetrieval", false),
                        Map.entry("useAdvisor", false),
                        Map.entry("naiveFullCorpusInPromptEnabled", true),
                        Map.entry("corpusGroundedDirectWorkflow", true),
                        Map.entry("naiveFullCorpusMaxChars", 32_000),
                        Map.entry("materializationStrategy", "CHUNK_LEVEL"),
                        // Explicitly off for determinism in Chat UI toggles.
                        Map.entry("metadataEnabled", false),
                        Map.entry("expansionEnabled", false),
                        Map.entry("nerEnabled", false),
                        Map.entry("toolsEnabled", false),
                        Map.entry("functionCallingEnabled", false),
                        Map.entry("reasoningEnabled", false),
                        Map.entry("rankerEnabled", false),
                        Map.entry("postRetrievalEnabled", false),
                        Map.entry("adaptiveRoutingEnabled", false),
                        Map.entry("judgeEnabled", false),
                        Map.entry("clarificationEnabled", false),
                        Map.entry("memoryEnabled", false),
                        // Keep numeric defaults stable across environments.
                        Map.entry("topK", 5),
                        Map.entry("similarityThreshold", 0.7)
                ),
                new IndexRequirements(RequiredMaterialization.CHUNK_LEVEL, false),
                false);

        define(
                RagExperimentalPresetCode.P1,
                uuid("cafe0001-0001-4001-8001-000000000011"),
                RagExperimentalPresetCode.P0,
                Map.of(
                        "useRetrieval", false,
                        "naiveFullCorpusInPromptEnabled", true,
                        "corpusGroundedDirectWorkflow", false,
                        "naiveFullCorpusMaxChars", 32_000,
                        "topK", 3,
                        "similarityThreshold", 0.9
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P2,
                uuid("cafe0001-0001-4001-8001-000000000012"),
                RagExperimentalPresetCode.P0,
                Map.of(
                        "useRetrieval", true,
                        "naiveFullCorpusInPromptEnabled", false,
                        "corpusGroundedDirectWorkflow", false,
                        "materializationStrategy", "DOCUMENT_LEVEL",
                        "topK", 8,
                        "similarityThreshold", 0.72
                ),
                new IndexRequirements(RequiredMaterialization.DOCUMENT_LEVEL, false),
                false);

        define(
                RagExperimentalPresetCode.P3,
                uuid("cafe0001-0001-4001-8001-000000000013"),
                RagExperimentalPresetCode.P2,
                Map.of(
                        "materializationStrategy", "CHUNK_LEVEL",
                        "topK", 10,
                        "similarityThreshold", 0.7
                ),
                new IndexRequirements(RequiredMaterialization.CHUNK_LEVEL, false),
                false);

        define(
                RagExperimentalPresetCode.P4,
                uuid("cafe0001-0001-4001-8001-000000000014"),
                RagExperimentalPresetCode.P3,
                Map.of(
                        "metadataEnabled", true
                ),
                new IndexRequirements(null, true),
                false);

        define(
                RagExperimentalPresetCode.P5,
                uuid("cafe0001-0001-4001-8001-000000000015"),
                RagExperimentalPresetCode.P4,
                Map.of(
                        "expansionEnabled", true,
                        "nerEnabled", true
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P6,
                uuid("cafe0001-0001-4001-8001-000000000016"),
                RagExperimentalPresetCode.P5,
                Map.of(
                        "reasoningEnabled", true
                        // Reasoning strategy remains environment-driven unless explicitly overridden.
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P7,
                uuid("cafe0001-0001-4001-8001-000000000017"),
                RagExperimentalPresetCode.P6,
                Map.of(
                        "toolsEnabled", true
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P8,
                uuid("cafe0001-0001-4001-8001-000000000018"),
                RagExperimentalPresetCode.P7,
                Map.of(
                        "materializationStrategy", "HYBRID",
                        "rankerEnabled", true,
                        "postRetrievalEnabled", true,
                        "topK", 12,
                        "similarityThreshold", 0.6
                ),
                new IndexRequirements(RequiredMaterialization.HYBRID, true),
                false);

        define(
                RagExperimentalPresetCode.P9,
                uuid("cafe0001-0001-4001-8001-000000000019"),
                RagExperimentalPresetCode.P8,
                Map.of(
                        "functionCallingEnabled", true
                        // Keep toolsEnabled=true from P7 for deterministic fallback; FC takes precedence.
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P10,
                uuid("cafe0001-0001-4001-8001-000000000020"),
                RagExperimentalPresetCode.P9,
                Map.of(
                        "useAdvisor", true
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P11,
                uuid("cafe0001-0001-4001-8001-000000000023"),
                RagExperimentalPresetCode.P10,
                Map.of(
                        "adaptiveRoutingEnabled", true
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P12,
                uuid("cafe0001-0001-4001-8001-000000000024"),
                RagExperimentalPresetCode.P11,
                Map.of(
                        "judgeEnabled", true
                ),
                null,
                false);

        define(
                RagExperimentalPresetCode.P13,
                uuid("cafe0001-0001-4001-8001-000000000021"),
                RagExperimentalPresetCode.P12,
                Map.of(
                        "clarificationEnabled", true
                ),
                null,
                true);

        define(
                RagExperimentalPresetCode.P14,
                uuid("cafe0001-0001-4001-8001-000000000022"),
                RagExperimentalPresetCode.P13,
                Map.of(
                        "memoryEnabled", true
                ),
                null,
                true);
    }

    private static void define(
            RagExperimentalPresetCode code,
            UUID productPresetId,
            RagExperimentalPresetCode parent,
            Map<String, Object> delta,
            IndexRequirements indexRequirementsDelta,
            boolean requiresMultiTurn) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(productPresetId, "productPresetId");
        PRESETS.put(
                code,
                new CanonicalPreset(
                        code,
                        productPresetId,
                        parent,
                        delta != null ? Map.copyOf(delta) : Map.of(),
                        indexRequirementsDelta,
                        requiresMultiTurn));
    }

    private static UUID uuid(String v) {
        return UUID.fromString(v);
    }

    public static CanonicalPreset require(RagExperimentalPresetCode code) {
        CanonicalPreset p = PRESETS.get(code);
        if (p == null) {
            throw new IllegalArgumentException("Unknown experimental preset code: " + code);
        }
        return p;
    }

    /** Stable seeded UUID used by Chat preset persistence. */
    public static UUID productPresetId(RagExperimentalPresetCode code) {
        return require(code).productPresetId();
    }

    /** Effective runtime overrides map (parent + delta), in deterministic insertion order. */
    public static Map<String, Object> effectiveRuntimeValues(RagExperimentalPresetCode code) {
        CanonicalPreset p = require(code);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (p.parent() != null) {
            out.putAll(effectiveRuntimeValues(p.parent()));
        }
        out.putAll(p.runtimeDelta());
        return Map.copyOf(out);
    }

    public static IndexRequirements effectiveIndexRequirements(RagExperimentalPresetCode code) {
        CanonicalPreset p = require(code);
        IndexRequirements base = p.parent() != null ? effectiveIndexRequirements(p.parent()) : IndexRequirements.none();
        IndexRequirements delta = p.indexRequirementsDelta();
        if (delta == null) {
            return base;
        }
        RequiredMaterialization requiredMaterialization =
                delta.requiredMaterialization() != null ? delta.requiredMaterialization() : base.requiredMaterialization();
        boolean requiresMetadata =
                base.requiresMetadataSupport() || delta.requiresMetadataSupport();
        return new IndexRequirements(requiredMaterialization, requiresMetadata);
    }

    /** Terminal runtime JSON for RagConfig.applyJsonOverrides (Lab evaluation harness). */
    public static ObjectNode effectiveTerminalRuntimeJson(RagExperimentalPresetCode code) {
        Map<String, Object> m = effectiveRuntimeValues(code);
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof Boolean b) {
                json.put(k, b);
            } else if (v instanceof Integer i) {
                json.put(k, i);
            } else if (v instanceof Long l) {
                json.put(k, l);
            } else if (v instanceof Double d) {
                json.put(k, d);
            } else if (v instanceof Number n) {
                json.put(k, n.doubleValue());
            } else if (v instanceof String s) {
                json.put(k, s);
            }
        }
        return json;
    }

    public static boolean requiresMultiTurn(RagExperimentalPresetCode code) {
        return require(code).requiresMultiTurn();
    }

    /** P0/P1 assemble documentary evidence from {@code vector_store} chunks bound to an index snapshot (Lab gate). */
    public static boolean requiresSnapshotAssembledCorpusEvidence(RagExperimentalPresetCode code) {
        return code == RagExperimentalPresetCode.P0 || code == RagExperimentalPresetCode.P1;
    }

    /**
     * All thesis experimental presets (P0–P14) are defined for project-scoped document-backed evaluation; the runtime
     * still enforces evidence availability per workflow (single-turn Lab uses P0–P12 only).
     */
    public static boolean corpusRequired(RagExperimentalPresetCode code) {
        return code != null && code.ordinal() <= RagExperimentalPresetCode.P14.ordinal();
    }

    /**
     * READY {@code PROJECT_SHARED} documents with storage are required before corpus assembly / retrieval can succeed.
     */
    public static boolean requiresProjectDocuments(RagExperimentalPresetCode code) {
        return corpusRequired(code);
    }

    /**
     * True when execution reads snapshot-bound {@code vector_store} rows (assembled corpus for P0/P1 or materialized index for P2+).
     */
    public static boolean requiresSnapshotForExecution(RagExperimentalPresetCode code) {
        if (code == null) {
            return false;
        }
        if (requiresSnapshotAssembledCorpusEvidence(code)) {
            return true;
        }
        RequiredMaterialization mat = effectiveIndexRequirements(code).requiredMaterialization();
        return mat != null && mat != RequiredMaterialization.NONE;
    }

    /** Single-turn Lab benchmark harness ({@code RAG_PRESET_END_TO_END}) supports P0–P12 only. */
    public static boolean singleTurnBenchmarkSelectable(RagExperimentalPresetCode code) {
        return code != null && code.ordinal() <= RagExperimentalPresetCode.P12.ordinal();
    }

    public static RagExperimentalPresetCode tryResolveCodeByProductPresetId(UUID presetId) {
        if (presetId == null) {
            return null;
        }
        for (CanonicalPreset p : PRESETS.values()) {
            if (presetId.equals(p.productPresetId())) {
                return p.code();
            }
        }
        return null;
    }
}

