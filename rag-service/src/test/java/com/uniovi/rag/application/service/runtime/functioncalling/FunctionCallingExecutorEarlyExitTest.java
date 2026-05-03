package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.application.service.runtime.tool.MeetingMinutesToolExecutionCore;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import com.uniovi.rag.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionCallingExecutorEarlyExitTest {

    @Mock private FunctionCallingToolRegistry toolRegistry;
    @Mock private MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    @Mock private FunctionCallingResultMapper resultMapper;
    @Mock private ResolvedRuntimeConfig resolvedRuntimeConfig;

    @BeforeEach
    void stubToolRegistry() {
        when(toolRegistry.callbacksFor(any())).thenReturn(List.of());
    }

    @Test
    void run_returnsModelDeclined_whenAssistantHasNoToolCalls() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(false);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(assistant);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any(OllamaOptions.class)).call().chatResponse())
                .thenReturn(chatResponse);

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClient, toolRegistry, meetingMinutesToolExecutionCore, resultMapper);

        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), minimalDecision());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.MODEL_DECLINED);
    }

    @Test
    void run_returnsInvalidModelOutput_whenMultipleToolCalls() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall a = mock(ToolCall.class);
        ToolCall b = mock(ToolCall.class);
        when(assistant.getToolCalls()).thenReturn(List.of(a, b));

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), decisionExposingAllMeetingTools());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.INVALID_MODEL_OUTPUT);
    }

    @Test
    void run_returnsInvalidModelOutput_whenToolNameUnknown() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall tc = mock(ToolCall.class);
        when(tc.name()).thenReturn("NOT_A_REAL_TOOL");
        when(assistant.getToolCalls()).thenReturn(List.of(tc));

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), decisionExposingAllMeetingTools());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.INVALID_MODEL_OUTPUT);
    }

    @Test
    void run_returnsInvalidModelOutput_whenArgumentsFailValidation() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall tc = mock(ToolCall.class);
        when(tc.name()).thenReturn("COUNT_DOCUMENTS_TOOL");
        when(tc.arguments()).thenReturn("{\"query\":\"not-matching-rewritten\"}");
        when(assistant.getToolCalls()).thenReturn(List.of(tc));

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), decisionExposingAllMeetingTools());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.INVALID_MODEL_OUTPUT);
    }

    @Test
    void run_returnsInvalidModelOutput_whenToolNotExposedInDecision() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall tc = mock(ToolCall.class);
        when(tc.name()).thenReturn("COUNT_DOCUMENTS_TOOL");
        when(assistant.getToolCalls()).thenReturn(List.of(tc));

        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.GET_FIELD_TOOL),
                        List.of(),
                        Optional.empty(),
                        "raw",
                        Map.of());

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r = executor.run(buildCtx(), minimalPlan(), decision);

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.INVALID_MODEL_OUTPUT);
    }

    @Test
    void run_returnsExecutedFailedInfra_whenToolExecutionFails() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall tc = mock(ToolCall.class);
        when(tc.name()).thenReturn("COUNT_DOCUMENTS_TOOL");
        when(tc.arguments()).thenReturn("{\"query\":\"rewritten\"}");
        when(assistant.getToolCalls()).thenReturn(List.of(tc));

        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(MeetingMinutesToolRawResult.runtimeFailure(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "down"));

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), decisionExposingAllMeetingTools());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.EXECUTED_FAILED_INFRA);
        assertThat(r.traceNotes()).anyMatch(n -> n.startsWith("tool_infra"));
    }

    @Test
    void run_returnsExecutedFailedInfra_whenMapperReturnsBlankStableText() {
        AssistantMessage assistant = mock(AssistantMessage.class);
        when(assistant.hasToolCalls()).thenReturn(true);
        ToolCall tc = mock(ToolCall.class);
        when(tc.name()).thenReturn("COUNT_DOCUMENTS_TOOL");
        when(tc.arguments()).thenReturn("{\"query\":\"rewritten\"}");
        when(assistant.getToolCalls()).thenReturn(List.of(tc));

        ToolResult raw = mock(ToolResult.class);
        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(MeetingMinutesToolRawResult.ok(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, raw));
        when(resultMapper.stableAnswerText(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).thenReturn("   ");
        when(resultMapper.normalizedPayload(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).thenReturn(Map.of("k", "v"));

        FunctionCallingExecutor executor =
                new FunctionCallingExecutor(
                        chatClientForAssistant(assistant), toolRegistry, meetingMinutesToolExecutionCore, resultMapper);
        FunctionCallingExecutionResult r =
                executor.run(buildCtx(), minimalPlan(), decisionExposingAllMeetingTools());

        assertThat(r.outcome()).isEqualTo(FunctionCallingOutcome.EXECUTED_FAILED_INFRA);
        assertThat(r.traceNotes()).contains("mapping_empty");
        assertThat(r.normalizedPayload()).containsEntry("k", "v");
    }

    private static ChatClient chatClientForAssistant(AssistantMessage assistant) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(assistant);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any(OllamaOptions.class)).call().chatResponse())
                .thenReturn(chatResponse);
        return chatClient;
    }

    private static FunctionCallingDecision decisionExposingAllMeetingTools() {
        return new FunctionCallingDecision(
                FunctionCallingMode.ENABLED,
                FunctionCallingOutcome.NOT_APPLICABLE,
                true,
                List.of(
                        DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                        DeterministicToolKind.FIND_PARAGRAPH_TOOL,
                        DeterministicToolKind.GET_FIELD_TOOL,
                        DeterministicToolKind.BOOLEAN_QUERY_TOOL,
                        DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL),
                List.of(),
                Optional.empty(),
                "raw",
                Map.of());
    }

    private static FunctionCallingDecision minimalDecision() {
        return new FunctionCallingDecision(
                FunctionCallingMode.ENABLED,
                FunctionCallingOutcome.NOT_APPLICABLE,
                true,
                List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                List.of(),
                Optional.empty(),
                "raw",
                Map.of());
    }

    private static QueryPlan minimalPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "rewritten",
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                rewrite,
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private ExecutionContext buildCtx() {
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolvedRuntimeConfig,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
