package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimePresetIndexRequirementsDto;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetBenchmarkGate;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class LabExperimentalPresetCatalogService {

    private static final List<String> ALLOWED_OUTCOMES =
            List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED");

    private final EvaluationReferenceBundleLoader referenceBundleLoader;
    private final RagFeatureConfiguration ragFeatureConfiguration;

    @Autowired
    public LabExperimentalPresetCatalogService(
            EvaluationReferenceBundleLoader referenceBundleLoader,
            RagFeatureConfiguration ragFeatureConfiguration) {
        this.referenceBundleLoader = referenceBundleLoader;
        this.ragFeatureConfiguration = ragFeatureConfiguration;
    }

    public List<ExperimentalPresetCatalogItemDto> list() {
        ReferenceBundleSnapshot snapshot = referenceBundleLoader.getSnapshot();
        Map<RagExperimentalPresetCode, RagPresetDefinition> defs = new EnumMap<>(RagExperimentalPresetCode.class);
        for (RagPresetDefinition d : snapshot.workbook().ragPresetCatalog()) {
            defs.put(d.presetId(), d);
        }
        List<ExperimentalPresetCatalogItemDto> out = new ArrayList<>();
        for (RagExperimentalPresetCode code : RagExperimentalPresetCode.values()) {
            RagPresetDefinition d = defs.get(code);
            // Canonical source of truth: effective runtime config must match Chat's seeded rag_preset.values.
            RagConfig effective = resolveEffectiveConfig(ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(code));

            Optional<String> blockedByLabHarness = ExperimentalPresetBenchmarkGate.blockReason(code);
            Optional<String> blockedByRuntime = runtimeBlockReason(effective);

            boolean runtimeOk = blockedByRuntime.isEmpty();
            boolean supported = runtimeOk;
            String supportStatus = supportStatus(code, runtimeOk, blockedByLabHarness.isPresent());
            List<String> requiredCapabilities = requiredCapabilities(effective);
            boolean chatSelectable = runtimeOk;
            boolean labSelectable = blockedByLabHarness.isEmpty();
            boolean labOnly = labSelectable && !chatSelectable;
            var idxReq = ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(code);
            boolean corpusReq = ExperimentalPresetCanonicalCatalog.corpusRequired(code);
            boolean snapReq = ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(code);
            boolean docsReq = ExperimentalPresetCanonicalCatalog.requiresProjectDocuments(code);
            boolean singleTurnBench = ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(code);
            ExperimentalPresetCanonicalCatalog.CanonicalPreset canon = ExperimentalPresetCanonicalCatalog.require(code);
            String parentCode = canon.parent() != null ? canon.parent().name() : null;
            String terminalJson = ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(code).toString();
            out.add(
                    new ExperimentalPresetCatalogItemDto(
                            experimentalProductPresetId(code),
                            code.name(),
                            d != null ? d.family() : "UNSPECIFIED",
                            d != null && !d.name().isBlank() ? d.name() : code.name(),
                            d != null ? d.objective() : "",
                            new RuntimePresetIndexRequirementsDto(
                                    idxReq.requiredMaterialization() != null ? idxReq.requiredMaterialization().name() : null,
                                    idxReq.requiresMetadataSupport()),
                            requiredCapabilities,
                            supported,
                            supportStatus,
                            blockedByRuntime.orElse(null),
                            requiresMultiTurn(code),
                            capabilities(d, code, effective),
                            ALLOWED_OUTCOMES,
                            chatSelectable,
                            labSelectable,
                            labOnly,
                            corpusReq,
                            snapReq,
                            docsReq,
                            singleTurnBench,
                            code.ordinal(),
                            parentCode,
                            terminalJson));
        }
        out.sort(Comparator.comparing(ExperimentalPresetCatalogItemDto::code));
        return out;
    }

    private static String supportStatus(
            RagExperimentalPresetCode code, boolean runtimeOk, boolean labBenchmarkBlocked) {
        if (!runtimeOk) {
            return "NOT_SUPPORTED";
        }
        if (labBenchmarkBlocked) {
            if (code == RagExperimentalPresetCode.P13 || code == RagExperimentalPresetCode.P14) {
                return "FUTURE_MULTI_TURN_NOT_SELECTABLE";
            }
            if (code == RagExperimentalPresetCode.P11 || code == RagExperimentalPresetCode.P12) {
                return "NOT_COMPARABLE_IN_SINGLE_TURN_LAB";
            }
        }
        return "EXECUTABLE";
    }

    private static boolean requiresMultiTurn(RagExperimentalPresetCode code) {
        // Canonical: only P13/P14 require multi-turn.
        return ExperimentalPresetCanonicalCatalog.requiresMultiTurn(code);
    }

    private RagConfig resolveEffectiveConfig(ObjectNode terminalJson) {
        // Build a minimal deterministic base and apply overlay keys (same keys as rag_preset.values).
        RagConfig base =
                RagConfig.fromFeatureConfiguration(
                        ragFeatureConfiguration,
                        10,
                        0.7,
                        null,
                        null,
                        null,
                        "SIMPLE");
        return RagConfig.applyJsonOverrides(base, terminalJson);
    }

    private static Optional<String> runtimeBlockReason(RagConfig rag) {
        if (rag.useAdvisor() && !rag.useRetrieval()) {
            return Optional.of("USE_ADVISOR_REQUIRES_RETRIEVAL");
        }
        if (rag.useRetrieval() && rag.materializationStrategy().name().equals("STRUCTURED_SEARCH")) {
            return Optional.of("STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED");
        }
        return Optional.empty();
    }

    private static List<String> requiredCapabilities(RagConfig rag) {
        Set<Capability> caps = CapabilitySet.fromRagConfig(rag).activeCapabilities();
        EnumSet<Capability> sorted = caps.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(caps);
        return sorted.stream().map(Enum::name).toList();
    }

    private static String experimentalProductPresetId(RagExperimentalPresetCode code) {
        return ExperimentalPresetCanonicalCatalog.productPresetId(code).toString();
    }

    private static Map<String, Object> capabilities(RagPresetDefinition d, RagExperimentalPresetCode code, RagConfig effective) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code.name());
        m.put("retrieval", d != null ? d.retrieval() : "");
        m.put("queryUnderstanding", d != null ? d.queryUnderstanding() : "");
        m.put("tools", d != null ? d.tools() : "");
        m.put("memory", d != null ? d.memory() : "");
        m.put("judges", d != null ? d.judges() : "");
        m.put("datasetPolicy", d != null ? d.datasetPolicy() : "");
        // Canonical resolved flags (same semantics as Chat terminal preset JSON + Lab overlay).
        m.put("runtimeFeatureFlags", effective.toValueMap());
        return m;
    }
}
