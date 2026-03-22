package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}
