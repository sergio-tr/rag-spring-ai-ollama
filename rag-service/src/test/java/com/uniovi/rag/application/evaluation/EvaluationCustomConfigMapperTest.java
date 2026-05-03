package com.uniovi.rag.application.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCustomConfigMapperTest {

    @Test
    void fromRequestBody_readsBooleansFromBooleanOrString_and_appliesImplOverrides() {
        RagImplementationProperties defaults = new RagImplementationProperties();
        defaults.setQueryServiceImpl("process");
        defaults.setRetrieverImpl("basic");
        defaults.setAnalyserImpl("minute-ner");

        EvaluationCustomConfigMapper mapper = new EvaluationCustomConfigMapper(defaults);

        var out =
                mapper.fromRequestBody(
                        Map.of(
                                "expansion", true,
                                "ner", "true",
                                "tools", "false",
                                "metadata", "true",
                                "post-retrieval", "false",
                                "query-service-impl", "simple",
                                "retriever-impl", "advanced",
                                "analyser-impl", "x"));

        RagFeatureConfiguration fc = out.features();
        assertThat(fc.isExpansionEnabled()).isTrue();
        assertThat(fc.isNerEnabled()).isTrue();
        assertThat(fc.isToolsEnabled()).isFalse();
        assertThat(fc.isMetadataEnabled()).isTrue();
        assertThat(fc.isPostRetrievalEnabled()).isFalse();
        assertThat(fc.isUseRetrieval()).isTrue();
        assertThat(fc.isUseAdvisor()).isTrue();

        assertThat(out.impl().getQueryServiceImpl()).isEqualTo("simple");
        assertThat(out.impl().getRetrieverImpl()).isEqualTo("advanced");
        assertThat(out.impl().getAnalyserImpl()).isEqualTo("x");
    }

    @Test
    void implementationsBlock_usesDefaults_whenImplValuesNull_and_switchesDocumentServiceByMetadataFlag() {
        RagImplementationProperties impl = new RagImplementationProperties();
        RagFeatureConfiguration fc = new RagFeatureConfiguration();

        EvaluationCustomConfigMapper mapper = new EvaluationCustomConfigMapper(new RagImplementationProperties());

        fc.setMetadataEnabled(false);
        Map<String, Object> block = mapper.implementationsBlock(fc, impl);
        @SuppressWarnings("unchecked")
        Map<String, Object> implementations = (Map<String, Object>) block.get("implementations");

        assertThat(implementations)
                .containsEntry("queryService", "process")
                .containsEntry("retriever", "basic")
                .containsEntry("analyser", "minute-ner")
                .containsEntry("documentService", "SimpleDocumentService");

        fc.setMetadataEnabled(true);
        Map<String, Object> block2 = mapper.implementationsBlock(fc, impl);
        @SuppressWarnings("unchecked")
        Map<String, Object> implementations2 = (Map<String, Object>) block2.get("implementations");
        assertThat(implementations2).containsEntry("documentService", "MetadataMinuteDocumentService");
    }
}

