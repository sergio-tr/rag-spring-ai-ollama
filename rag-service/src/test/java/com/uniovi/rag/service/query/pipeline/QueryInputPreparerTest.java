package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryInputPreparerTest {

    @Mock
    private RagFeatureConfiguration featureConfig;

    @Mock
    private QueryExpander expander;

    @Mock
    private QueryAnalyser analyser;

    @Mock
    private QueryClassifier classifier;

    @InjectMocks
    private QueryInputPreparer preparer;

    @Test
    void prepare_skipsExpansionNerAndTools_whenAllDisabled() {
        when(featureConfig.isExpansionEnabled()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(false);

        PreparedQuery pq = preparer.prepare("hello");

        assertThat(pq.expandedQuery()).isEqualTo("hello");
        assertThat(pq.nerEntities()).isNull();
        assertThat(pq.queryType()).isNull();
        verify(expander, never()).expand(org.mockito.ArgumentMatchers.anyString());
        verify(analyser, never()).analyse(org.mockito.ArgumentMatchers.anyString());
        verify(classifier, never()).classify(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void prepare_runsExpansion_whenEnabled() {
        when(featureConfig.isExpansionEnabled()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(false);
        when(expander.expand("a")).thenReturn("b");

        PreparedQuery pq = preparer.prepare("a");

        assertThat(pq.expandedQuery()).isEqualTo("b");
    }

    @Test
    void prepare_runsNer_whenEnabled() {
        when(featureConfig.isExpansionEnabled()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(true);
        when(featureConfig.isToolsEnabled()).thenReturn(false);
        JSONObject ner = new JSONObject();
        ner.put("k", "v");
        when(analyser.analyse("q")).thenReturn(ner);

        PreparedQuery pq = preparer.prepare("q");

        assertThat(pq.nerEntities().getString("k")).isEqualTo("v");
    }

    @Test
    void prepare_runsClassifierAndOverrides_whenToolsEnabled() {
        when(featureConfig.isExpansionEnabled()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(classifier.classify(eq("q"))).thenReturn(QueryType.COUNT_DOCUMENTS);

        PreparedQuery pq = preparer.prepare("q");

        assertThat(pq.queryType()).isEqualTo(QueryType.COUNT_DOCUMENTS);
    }
}
