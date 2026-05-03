package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.Cluster;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracedDocumentContentExtractorTest {

    @Mock private DocumentContentExtractor delegate;
    @Mock private ObservabilitySupport observability;

    @Test
    void extractDate_wrapsDelegateInSpan_andRecordsCounter() {
        when(observability.runWithSpan(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(3, Supplier.class).get());
        when(delegate.extractDate("abc")).thenReturn("2026-01-01");

        TracedDocumentContentExtractor traced = new TracedDocumentContentExtractor(delegate, observability);
        String out = traced.extractDate("abc");

        assertThat(out).isEqualTo("2026-01-01");
        verify(observability).recordCounter("rag.extraction.calls", "operation", "extractDate");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> attrsCap = ArgumentCaptor.forClass(Map.class);
        verify(observability).runWithSpan(eq("rag.extraction.extractDate"), attrsCap.capture(), eq("result"), any());
        assertThat(attrsCap.getValue()).containsEntry("contentLength", "3").containsEntry("operation", "extractDate");
    }

    @Test
    void extractRelevantFragment_truncatesQueryAttribute() {
        when(observability.runWithSpan(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(3, Supplier.class).get());
        when(delegate.extractRelevantFragment(anyString(), anyString())).thenReturn("frag");

        String longQuery = "q".repeat(600);

        TracedDocumentContentExtractor traced = new TracedDocumentContentExtractor(delegate, observability);
        String out = traced.extractRelevantFragment("content", longQuery);

        assertThat(out).isEqualTo("frag");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> attrsCap = ArgumentCaptor.forClass(Map.class);
        verify(observability).runWithSpan(eq("rag.extraction.extractRelevantFragment"), attrsCap.capture(), eq("result"), any());
        assertThat(attrsCap.getValue().get("query")).endsWith("...").hasSizeLessThanOrEqualTo(503);
    }

    @Test
    void extractAttendees_usesSpanWithoutResultTag_andReturnsDelegateValue() {
        when(observability.runWithSpan(anyString(), any(), eq((String) null), any()))
                .thenAnswer(inv -> inv.getArgument(3, Supplier.class).get());
        when(delegate.extractAttendees("c")).thenReturn(List.of("a", "b"));

        TracedDocumentContentExtractor traced = new TracedDocumentContentExtractor(delegate, observability);
        List<String> out = traced.extractAttendees("c");

        assertThat(out).containsExactly("a", "b");
        verify(observability).recordCounter("rag.extraction.calls", "operation", "extractAttendees");
        verify(observability).runWithSpan(eq("rag.extraction.extractAttendees"), any(), eq((String) null), any());
    }

    @Test
    void clusterItems_recordsCounter_andRunsSpan() {
        @SuppressWarnings("unchecked")
        List<Cluster<String>> clusters = (List<Cluster<String>>) (List<?>) List.<Cluster<?>>of();

        when(observability.runWithSpan(anyString(), any(), eq((String) null), any()))
                .thenAnswer(inv -> inv.getArgument(3, Supplier.class).get());
        @SuppressWarnings("unchecked")
        List<Cluster<Object>> clustersAsObject = (List<Cluster<Object>>) (List<?>) clusters;
        when(delegate.clusterItems(any(), any(), any(), eq(0.7))).thenReturn(clustersAsObject);

        TracedDocumentContentExtractor traced = new TracedDocumentContentExtractor(delegate, observability);
        List<Cluster<String>> out =
                traced.clusterItems(List.of("x"), Function.identity(), s -> "t", 0.7);

        assertThat(out).isSameAs(clusters);
        verify(observability).recordCounter("rag.extraction.calls", "operation", "clusterItems");
        verify(observability).runWithSpan(eq("rag.extraction.clusterItems"), any(), eq((String) null), any());
    }
}

