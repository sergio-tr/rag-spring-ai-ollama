package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LabPresetCatalogBridgeTest {

    @Test
    void p15_withoutWorkbookRow_resolvesSyntheticFromCanonicalCatalog() {
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook = workbookWithThroughP14();

        assertThat(LabPresetCatalogBridge.resolve(RagExperimentalPresetCode.P15, workbook))
                .isPresent()
                .get()
                .satisfies(
                        def -> {
                            assertThat(def.presetId()).isEqualTo(RagExperimentalPresetCode.P15);
                            assertThat(def.name()).isEqualTo("P15");
                            assertThat(def.retrieval()).isEqualTo(workbook.get(RagExperimentalPresetCode.P9).retrieval());
                            assertThat(def.tools()).isEqualTo(workbook.get(RagExperimentalPresetCode.P9).tools());
                        });
    }

    @Test
    void p15_syntheticDefinition_inheritsP9WorkbookFields() {
        RagPresetDefinition p9 =
                new RagPresetDefinition(
                        RagExperimentalPresetCode.P9,
                        "retrieval-tools",
                        "P9",
                        "hybrid",
                        "classifier",
                        "fc",
                        "",
                        "",
                        "MAIN",
                        "function calling baseline",
                        "gold");
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook =
                Map.of(RagExperimentalPresetCode.P9, p9);

        RagPresetDefinition p15 =
                LabPresetCatalogBridge.resolve(RagExperimentalPresetCode.P15, workbook).orElseThrow();

        assertThat(p15.family()).isEqualTo("retrieval-tools");
        assertThat(p15.queryUnderstanding()).isEqualTo("classifier");
        assertThat(p15.datasetPolicy()).isEqualTo("gold");
        assertThat(p15.objective()).contains("function calling baseline");
    }

    @Test
    void p15_effectiveRuntime_includesAdaptiveRouting() {
        Map<String, Object> p9 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P9);
        Map<String, Object> p15 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P15);

        assertThat(p15.get("adaptiveRoutingEnabled")).isEqualTo(true);
        assertThat(p15.get("functionCallingEnabled")).isEqualTo(p9.get("functionCallingEnabled"));
        assertThat(p15.get("materializationStrategy")).isEqualTo(p9.get("materializationStrategy"));
    }

    @Test
    void workbookRow_winsOverCanonicalFallback() {
        RagPresetDefinition workbookP15 =
                new RagPresetDefinition(
                        RagExperimentalPresetCode.P15,
                        "custom",
                        "Workbook P15",
                        "r",
                        "q",
                        "t",
                        "",
                        "",
                        "",
                        "obj",
                        "");
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook =
                Map.of(RagExperimentalPresetCode.P15, workbookP15);

        assertThat(LabPresetCatalogBridge.resolve(RagExperimentalPresetCode.P15, workbook))
                .contains(workbookP15);
    }

    @Test
    void p11_withoutWorkbookRow_bridgeDoesNotSynthesize() {
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook = workbookThroughP10Only();

        assertThat(LabPresetCatalogBridge.resolve(RagExperimentalPresetCode.P11, workbook)).isEmpty();
    }

    @Test
    void p0ThroughP14_workbookRowsRemainPreferred() {
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook = workbookWithThroughP14();

        for (RagExperimentalPresetCode code : RagExperimentalPresetCode.values()) {
            if (code.ordinal() <= RagExperimentalPresetCode.P14.ordinal()) {
                assertThat(LabPresetCatalogBridge.resolve(code, workbook)).contains(workbook.get(code));
            }
        }
    }

    private static Map<RagExperimentalPresetCode, RagPresetDefinition> workbookThroughP10Only() {
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook = new EnumMap<>(RagExperimentalPresetCode.class);
        for (RagExperimentalPresetCode code : RagExperimentalPresetCode.values()) {
            if (code.ordinal() <= RagExperimentalPresetCode.P10.ordinal()) {
                workbook.put(
                        code,
                        new RagPresetDefinition(
                                code,
                                "family-" + code.name(),
                                code.name(),
                                "retrieval",
                                "qu",
                                "tools",
                                "",
                                "",
                                "MAIN",
                                "objective",
                                "dataset"));
            }
        }
        return workbook;
    }

    private static Map<RagExperimentalPresetCode, RagPresetDefinition> workbookWithThroughP14() {
        Map<RagExperimentalPresetCode, RagPresetDefinition> workbook = new EnumMap<>(RagExperimentalPresetCode.class);
        for (RagExperimentalPresetCode code : RagExperimentalPresetCode.values()) {
            if (code.ordinal() <= RagExperimentalPresetCode.P14.ordinal()) {
                workbook.put(
                        code,
                        new RagPresetDefinition(
                                code,
                                "family-" + code.name(),
                                code.name(),
                                "retrieval",
                                "qu",
                                "tools",
                                "",
                                "",
                                "MAIN",
                                "objective",
                                "dataset"));
            }
        }
        return workbook;
    }
}
