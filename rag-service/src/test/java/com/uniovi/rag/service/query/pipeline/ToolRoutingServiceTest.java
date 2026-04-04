package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolRoutingServiceTest {

    @Test
    void tryMainToolsBlock_skipsAdapter_whenToolsEnabledAndQueryTypeNullAndFunctionCallingOff() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);

        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(any(), anyString())).thenAnswer(inv -> {
            throw new AssertionError("adapter.execute must not run when QueryType is null");
        });

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));

        Optional<?> out = svc.tryMainToolsBlock(null, "pregunta de prueba");

        assertTrue(out.isEmpty());
        verify(adapter, never()).execute(any(), anyString());
    }

    @Test
    void tryPreferToolForDate_skipsAdapter_whenQueryTypeNull() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(true);
        when(featureConfig.isToolsEnabled()).thenReturn(true);

        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(any(), anyString())).thenAnswer(inv -> {
            throw new AssertionError("adapter.execute must not run when QueryType is null");
        });

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));

        Optional<?> out = svc.tryPreferToolForDate(null, new JSONObject(), "x");

        assertTrue(out.isEmpty());
        verify(adapter, never()).execute(any(), anyString());
    }

    @Test
    void tryMainToolsBlock_invokesAdapter_whenToolsEnabledAndQueryTypePresent() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);

        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenReturn(new ToolResult("ok", "adapter"));
        ResponseValidator responseValidator = mock(ResponseValidator.class);
        when(responseValidator.validateAndClean(eq("ok"), anyString())).thenReturn("ok");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                responseValidator,
                mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "cual es la fecha").isPresent());
        verify(adapter).execute(eq(QueryType.GET_FIELD), anyString());
    }

    @Test
    void tryToolRoute_whenToolsDisabled_returnsNull() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(false);
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                mock(MeetingMinutesToolsAdapter.class),
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertNull(svc.tryToolRoute("query", new JSONObject(), QueryType.GET_FIELD));
    }

    @Test
    void tryToolRoute_whenQueryTypeNull_returnsNull() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                mock(MeetingMinutesToolsAdapter.class),
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertNull(svc.tryToolRoute("query", new JSONObject(), null));
    }

    @Test
    void tryToolRoute_whenNoToolRegistered_returnsNull() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        RagToolsConfiguration toolsConfig = mock(RagToolsConfiguration.class);
        when(toolsConfig.getTool(QueryType.GET_FIELD)).thenReturn(null);
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                toolsConfig,
                mock(MeetingMinutesToolsAdapter.class),
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertNull(svc.tryToolRoute("short", new JSONObject(), QueryType.GET_FIELD));
    }

    @Test
    void tryToolRoute_whenToolReturnsValid_returnsValidatedResult() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        RagToolsConfiguration toolsConfig = mock(RagToolsConfiguration.class);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.BOOLEAN_QUERY)).thenReturn(tool);
        when(tool.execute(any(ToolExecutionContext.class))).thenReturn(new ToolResult("valid tool output here", "src"));

        ResponseValidator responseValidator = mock(ResponseValidator.class);
        when(responseValidator.validateAndClean(eq("valid tool output here"), anyString())).thenReturn("valid tool output here");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                toolsConfig,
                mock(MeetingMinutesToolsAdapter.class),
                responseValidator,
                mock(ChatRequestSpecFactory.class));

        ToolResult out = svc.tryToolRoute("question text", new JSONObject(), QueryType.BOOLEAN_QUERY);
        assertNotNull(out);
        assertEquals("valid tool output here", out.result());
    }

    @Test
    void tryMainToolsBlock_functionCallingReturnsValidated() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(true);
        when(featureConfig.isToolsEnabled()).thenReturn(false);

        ChatRequestSpecFactory factory = mock(ChatRequestSpecFactory.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(factory.spec()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(requestSpec.tools(adapter)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("fc out");

        ResponseValidator rv = mock(ResponseValidator.class);
        when(rv.validateAndClean(eq("fc out"), anyString())).thenReturn("fc out");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                rv,
                factory);

        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "q").isPresent());
    }

    @Test
    void tryPreferToolForDate_withDateAndGetField_usesAdapter() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(true);

        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenReturn(new ToolResult("ok", "a"));

        ResponseValidator rv = mock(ResponseValidator.class);
        when(rv.validateAndClean(eq("ok"), anyString())).thenReturn("ok");

        JSONObject ner = new JSONObject();
        ner.put("date", "2024-01-01");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                rv,
                mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryPreferToolForDate(QueryType.GET_FIELD, ner, "expanded").isPresent());
    }

    @Test
    void tryToolRoute_nerEnabled_passesContextWithNer() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(true);
        RagToolsConfiguration toolsConfig = mock(RagToolsConfiguration.class);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(tool);
        when(tool.execute(any(ToolExecutionContext.class))).thenReturn(new ToolResult("1", "s"));

        ResponseValidator responseValidator = mock(ResponseValidator.class);
        when(responseValidator.validateAndClean(anyString(), anyString())).thenReturn("1");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                toolsConfig,
                mock(MeetingMinutesToolsAdapter.class),
                responseValidator,
                mock(ChatRequestSpecFactory.class));

        JSONObject ner = new JSONObject();
        ner.put("k", "v");
        ToolResult out = svc.tryToolRoute("q", ner, QueryType.COUNT_DOCUMENTS);
        assertNotNull(out);
        verify(tool).execute(any(ToolExecutionContext.class));
    }

    @Test
    void tryMainToolsBlock_adapterNull_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                null,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "q").isEmpty());
    }

    @Test
    void tryPreferToolForDate_adapterNull_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        JSONObject ner = new JSONObject();
        ner.put("date", "2024-02-02");
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                null,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertTrue(svc.tryPreferToolForDate(QueryType.GET_FIELD, ner, "x").isEmpty());
    }

    @Test
    void tryPreferToolForDate_adapterPathDisabled_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(false);
        JSONObject ner = new JSONObject();
        ner.put("date", "2024-02-02");
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                mock(MeetingMinutesToolsAdapter.class),
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertTrue(svc.tryPreferToolForDate(QueryType.GET_FIELD, ner, "x").isEmpty());
    }

    @Test
    void tryPreferToolForDate_withoutDateInNer_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));
        assertTrue(svc.tryPreferToolForDate(QueryType.GET_FIELD, new JSONObject(), "x").isEmpty());
        verify(adapter, never()).execute(any(), anyString());
    }

    @Test
    void tryPreferToolForDate_decisionExtractionWithFecha_callsAdapter() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.DECISION_EXTRACTION), anyString()))
                .thenReturn(new ToolResult("decision", "adp"));
        ResponseValidator rv = mock(ResponseValidator.class);
        when(rv.validateAndClean(eq("decision"), anyString())).thenReturn("decision");

        JSONObject ner = new JSONObject();
        ner.put("fecha", "2024-06-01");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig, mock(RagToolsConfiguration.class), adapter, rv, mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryPreferToolForDate(QueryType.DECISION_EXTRACTION, ner, "expanded").isPresent());
    }

    @Test
    void tryMainToolsBlock_functionCallingEmpty_thenDeterministicAdapter() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(true);
        when(featureConfig.isToolsEnabled()).thenReturn(true);

        ChatRequestSpecFactory factory = mock(ChatRequestSpecFactory.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(factory.spec()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(requestSpec.tools(adapter)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("");

        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenReturn(new ToolResult("adapter-out", "s"));
        ResponseValidator rv = mock(ResponseValidator.class);
        when(rv.validateAndClean(eq("adapter-out"), anyString())).thenReturn("adapter-out");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig, mock(RagToolsConfiguration.class), adapter, rv, factory);

        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "q").isPresent());
        verify(adapter).execute(eq(QueryType.GET_FIELD), anyString());
    }

    @Test
    void tryMainToolsBlock_adapterThrows_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenThrow(new RuntimeException("adapter boom"));

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "q").isEmpty());
    }

    @Test
    void tryMainToolsBlock_adapterResultRejectedByValidator_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenReturn(new ToolResult("raw", "s"));
        ResponseValidator rv = mock(ResponseValidator.class);
        when(rv.validateAndClean(eq("raw"), anyString())).thenReturn("");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig, mock(RagToolsConfiguration.class), adapter, rv, mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryMainToolsBlock(QueryType.GET_FIELD, "q").isEmpty());
    }

    @Test
    void tryPreferToolForDate_adapterThrows_returnsEmpty() {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isFunctionCallingEnabled()).thenReturn(false);
        MeetingMinutesToolsAdapter adapter = mock(MeetingMinutesToolsAdapter.class);
        when(adapter.execute(eq(QueryType.GET_FIELD), anyString())).thenThrow(new RuntimeException("prefer boom"));

        JSONObject ner = new JSONObject();
        ner.put("dates", "2024-01-01");

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                mock(RagToolsConfiguration.class),
                adapter,
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));

        assertTrue(svc.tryPreferToolForDate(QueryType.GET_FIELD, ner, "e").isEmpty());
    }

    @Test
    void tryToolRoute_duplicateElementMessage_doesNotRetryEndlessly() throws Exception {
        RagFeatureConfiguration featureConfig = mock(RagFeatureConfiguration.class);
        when(featureConfig.isToolsEnabled()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        RagToolsConfiguration toolsConfig = mock(RagToolsConfiguration.class);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.SUMMARIZE_MEETING)).thenReturn(tool);
        when(tool.execute(any(ToolExecutionContext.class)))
                .thenThrow(new RuntimeException("duplicate element in stream"));

        ToolRoutingService svc = new ToolRoutingService(
                featureConfig,
                toolsConfig,
                mock(MeetingMinutesToolsAdapter.class),
                mock(ResponseValidator.class),
                mock(ChatRequestSpecFactory.class));

        assertNull(svc.tryToolRoute("q", new JSONObject(), QueryType.SUMMARIZE_MEETING));
        verify(tool, times(1)).execute(any(ToolExecutionContext.class));
    }
}
