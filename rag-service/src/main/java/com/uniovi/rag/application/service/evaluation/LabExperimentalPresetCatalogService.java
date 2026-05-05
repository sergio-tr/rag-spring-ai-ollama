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
import com.uniovi.rag.service.evaluation.preset.ExperimentalPresetBenchmarkGate;
import com.uniovi.rag.service.evaluation.preset.RagPresetExperimentalOverlay;
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
            RagPresetExperimentalOverlay.Overlay overlay =
                    RagPresetExperimentalOverlay.build(ragFeatureConfiguration, code);
            RagConfig effective = resolveEffectiveConfig(overlay.terminalRuntimeJson());

            Optional<String> blockedByLabHarness = ExperimentalPresetBenchmarkGate.blockReason(code);
            Optional<String> blockedByRuntime = runtimeBlockReason(effective);
            Optional<String> blocked = blockedByLabHarness.isPresent() ? blockedByLabHarness : blockedByRuntime;

            String supportStatus = supportStatus(code, blocked);
            boolean supported = blocked.isEmpty();
            List<String> requiredCapabilities = requiredCapabilities(effective);
            boolean chatSelectable = supported && !requiresMultiTurn(code);
            boolean labSelectable = true;
            out.add(
                    new ExperimentalPresetCatalogItemDto(
                            experimentalProductPresetId(code),
                            code.name(),
                            d != null ? d.family() : "UNSPECIFIED",
                            d != null && !d.name().isBlank() ? d.name() : code.name(),
                            d != null ? d.objective() : "",
                            requiredCapabilities,
                            supported,
                            supportStatus,
                            blocked.orElse(null),
                            requiresMultiTurn(code),
                            capabilities(d, code),
                            ALLOWED_OUTCOMES,
                            chatSelectable,
                            labSelectable));
        }
        out.sort(Comparator.comparing(ExperimentalPresetCatalogItemDto::code));
        return out;
    }

    private static String supportStatus(RagExperimentalPresetCode code, Optional<String> blocked) {
        if (blocked.isPresent()) {
            return switch (code) {
                case P11, P12 -> "REQUIRES_MULTI_TURN";
                default -> "NOT_SUPPORTED";
            };
        }
        return "EXECUTABLE";
    }

    private static boolean requiresMultiTurn(RagExperimentalPresetCode code) {
        return code == RagExperimentalPresetCode.P11 || code == RagExperimentalPresetCode.P12;
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
        if (rag.reasoningEnabled() || rag.rankerEnabled() || rag.postRetrievalEnabled()) {
            return Optional.of("ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED");
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
        // Stable IDs seeded in V19__tfg_experimental_presets_p0_p14.sql
        UUID id =
                switch (code) {
                    case P0 -> UUID.fromString("cafe0001-0001-4001-8001-000000000010");
                    case P1 -> UUID.fromString("cafe0001-0001-4001-8001-000000000011");
                    case P2 -> UUID.fromString("cafe0001-0001-4001-8001-000000000012");
                    case P3 -> UUID.fromString("cafe0001-0001-4001-8001-000000000013");
                    case P4 -> UUID.fromString("cafe0001-0001-4001-8001-000000000014");
                    case P5 -> UUID.fromString("cafe0001-0001-4001-8001-000000000015");
                    case P6 -> UUID.fromString("cafe0001-0001-4001-8001-000000000016");
                    case P7 -> UUID.fromString("cafe0001-0001-4001-8001-000000000017");
                    case P8 -> UUID.fromString("cafe0001-0001-4001-8001-000000000018");
                    case P9 -> UUID.fromString("cafe0001-0001-4001-8001-000000000019");
                    case P10 -> UUID.fromString("cafe0001-0001-4001-8001-000000000020");
                    case P11 -> UUID.fromString("cafe0001-0001-4001-8001-000000000021");
                    case P12 -> UUID.fromString("cafe0001-0001-4001-8001-000000000022");
                    case P13 -> UUID.fromString("cafe0001-0001-4001-8001-000000000023");
                    case P14 -> UUID.fromString("cafe0001-0001-4001-8001-000000000024");
                };
        return id.toString();
    }

    private static Map<String, Object> capabilities(RagPresetDefinition d, RagExperimentalPresetCode code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code.name());
        m.put("retrieval", d != null ? d.retrieval() : "");
        m.put("queryUnderstanding", d != null ? d.queryUnderstanding() : "");
        m.put("tools", d != null ? d.tools() : "");
        m.put("memory", d != null ? d.memory() : "");
        m.put("judges", d != null ? d.judges() : "");
        m.put("datasetPolicy", d != null ? d.datasetPolicy() : "");
        return m;
    }
}
