package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.tool.CountDocumentsTool;
import com.uniovi.rag.tool.ToolResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TracedDateExistenceGuard}.
 */
class TracedDateExistenceGuardTest {

    private DateExistenceGuard delegate;
    private TracedDateExistenceGuard traced;

    @BeforeEach
    void setUp() {
        delegate = mock(DateExistenceGuard.class);
        ObservabilitySupport obs = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedDateExistenceGuard(delegate, obs);
    }

    @Test
    void checkNoActaForDate_delegates() {
        ToolResult tr = ToolResult.from("none", CountDocumentsTool.class);
        when(delegate.checkNoActaForDate(anyString(), any(), any())).thenReturn(Optional.of(tr));

        JSONObject ner = new JSONObject();
        Optional<ToolResult> out = traced.checkNoActaForDate("q", QueryType.GET_FIELD, ner);
        assertTrue(out.isPresent());
        assertEquals(tr, out.get());
        verify(delegate).checkNoActaForDate(eq("q"), eq(QueryType.GET_FIELD), any(JSONObject.class));
    }
}
