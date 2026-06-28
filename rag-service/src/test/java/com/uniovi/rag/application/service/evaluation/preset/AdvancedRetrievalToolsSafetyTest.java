package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.metrics.RagPresetAdvancedRetrievalMetrics;
import com.uniovi.rag.application.service.evaluation.metrics.RagPresetToolMetrics;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedRetrievalToolsSafetyTest {

    @Test
    void p8Catalog_keepsDeterministicToolsDisabledWhileMetadataCompatibilityRemains() {
        Map<String, Object> p8 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P8);

        assertThat(p8.get("toolsEnabled")).isEqualTo(true);
        assertThat(p8.get("deterministicToolRoutingEnabled")).isEqualTo(false);
        assertThat(p8.get("functionCallingEnabled")).isEqualTo(false);
        assertThat(p8.get("useAdvisor")).isEqualTo(false);
    }

    @Test
    void p8ExportShape_doesNotMarkDeterministicToolRouteOrFunctionCallingUsed() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put("outcome", "EXECUTED");
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("denseCandidateCount", 2);
        mp.put("sparseCandidateCount", 0);
        mp.put("mergedCandidateCount", 2);
        mp.put(RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE, false);
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED, false);
        mp.put(RagPresetToolMetrics.KEY_TOOL_EXECUTED, false);
        mp.put("deterministicToolRoutingEnabled", false);

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)).isEqualTo(true);
        assertThat(mp.get(RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE)).isEqualTo(false);
        assertThat(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED)).isEqualTo(false);
    }
}
