package com.uniovi.rag.service.query;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QueryService} default method + Mockito contract.
 */
class QueryServiceTest {

    @Test
    void generateResponse_oneArg_delegatesToTwoArgWithNullModel() {
        QueryService svc = mock(QueryService.class, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        QueryResponse resp = QueryResponse.fromLLM("x", QueryType.BOOLEAN_QUERY);
        when(svc.generateResponse("q", null)).thenReturn(resp);

        assertThat(svc.generateResponse("q")).isSameAs(resp);
        verify(svc).generateResponse("q", null);
    }

    @Test
    void generateResponse_explicitStubbingWorks() {
        QueryService svc = mock(QueryService.class);
        QueryResponse resp = QueryResponse.fromLLM("y", QueryType.GET_FIELD);
        when(svc.generateResponse("q", "m")).thenReturn(resp);

        assertThat(svc.generateResponse("q", "m")).isSameAs(resp);
    }
}
