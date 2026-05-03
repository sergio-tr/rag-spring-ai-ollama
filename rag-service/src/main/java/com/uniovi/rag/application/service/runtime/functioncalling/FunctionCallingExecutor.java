package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.tool.MeetingMinutesToolExecutionCore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring AI protocol + ChatClient orchestration for bounded function calling.
 */
@Component
public class FunctionCallingExecutor {

    private final ChatClient chatClient;
    private final FunctionCallingToolRegistry toolRegistry;
    private final MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    private final FunctionCallingResultMapper resultMapper;

    public FunctionCallingExecutor(
            ChatClient chatClient,
            FunctionCallingToolRegistry toolRegistry,
            MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore,
            FunctionCallingResultMapper resultMapper) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.meetingMinutesToolExecutionCore = meetingMinutesToolExecutionCore;
        this.resultMapper = resultMapper;
    }

    public FunctionCallingExecutionResult run(ExecutionContext ctx, QueryPlan plan, FunctionCallingDecision decision) {
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String msgBase = "outcome=pending";
        try {
            String firstUser = FunctionCallingPrompts.buildFirstRoundUserMessage(plan);
            List<ToolCallback> callbacks = toolRegistry.callbacksFor(decision.exposedToolKinds());
            List<FunctionCallback> asFunctions = new ArrayList<>(callbacks);
            OllamaOptions.Builder optBuilder =
                    OllamaOptions.builder().internalToolExecutionEnabled(false).toolCallbacks(asFunctions);
            ctx.chatModelOverride()
                    .filter(m -> m != null && !m.isBlank())
                    .ifPresent(optBuilder::model);

            ChatResponse response1 =
                    chatClient
                            .prompt()
                            .system(ctx.effectiveSystemPrompt())
                            .user(firstUser)
                            .options(optBuilder.build())
                            .call()
                            .chatResponse();

            stages.add(new ExecutionStageTrace(
                    "function_calling_model",
                    0L,
                    ExecutionStageOutcome.SUCCESS,
                    msgBase + " round=tool_enabled"));

            AssistantMessage assistant = (AssistantMessage) response1.getResult().getOutput();
            if (!assistant.hasToolCalls() || assistant.getToolCalls().isEmpty()) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.MODEL_DECLINED));
                return terminalOutcome(
                        FunctionCallingOutcome.MODEL_DECLINED,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("model_declined"));
            }
            if (assistant.getToolCalls().size() != 1) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
                return terminalOutcome(
                        FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("multiple_tool_calls"));
            }
            ToolCall tc = assistant.getToolCalls().getFirst();
            DeterministicToolKind kind =
                    DeterministicToolKindMappings.fromDeclaredToolName(tc.name()).orElse(null);
            if (kind == null) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
                return terminalOutcome(
                        FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("unknown_tool_name"));
            }
            Set<DeterministicToolKind> allowed =
                    decision.exposedToolKinds().stream().collect(Collectors.toSet());
            if (!allowed.contains(kind)) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
                return terminalOutcome(
                        FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("tool_not_exposed"));
            }
            try {
                FcToolArgumentParser.parseOrThrow(tc.arguments(), kind, plan);
            } catch (IllegalArgumentException e) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
                return terminalOutcome(
                        FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("bad_args:" + e.getMessage()));
            }

            MeetingMinutesToolRawResult raw = meetingMinutesToolExecutionCore.execute(kind, ctx, plan);
            if (raw.status() != MeetingMinutesToolRawResult.Status.OK) {
                stages.add(new ExecutionStageTrace(
                        "function_calling_tool",
                        0L,
                        ExecutionStageOutcome.FAILED,
                        "tool_status=" + raw.status()));
                stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
                return terminalOutcome(
                        FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                        "",
                        Map.of("error", raw.errorDetail().orElse("tool_failed")),
                        Optional.of(kind),
                        stages,
                        List.of("tool_infra:" + raw.status()));
            }

            stages.add(new ExecutionStageTrace(
                    "function_calling_tool",
                    0L,
                    ExecutionStageOutcome.SUCCESS,
                    "kind=" + kind));

            String stableText = resultMapper.stableAnswerText(raw.raw().orElseThrow(), kind);
            Map<String, Object> payload = resultMapper.normalizedPayload(raw.raw().orElseThrow(), kind);
            if (stableText.isBlank()) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
                return terminalOutcome(
                        FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                        "",
                        payload,
                        Optional.of(kind),
                        stages,
                        List.of("mapping_empty"));
            }

            String followUpUser = FunctionCallingPrompts.buildFollowUpUserMessage(plan, payload);
            var followSpec = chatClient.prompt().system(ctx.effectiveSystemPrompt()).user(followUpUser);
            var chatModelOverride = ctx.chatModelOverride();
            if (chatModelOverride.isPresent() && !chatModelOverride.get().isBlank()) {
                followSpec =
                        followSpec.options(
                                OllamaOptions.builder().model(chatModelOverride.get().trim()).build());
            }

            ChatResponse response2 = followSpec.call().chatResponse();
            stages.add(new ExecutionStageTrace(
                    "function_calling_model",
                    0L,
                    ExecutionStageOutcome.SUCCESS,
                    msgBase + " round=follow_up"));

            AssistantMessage followAssistant = (AssistantMessage) response2.getResult().getOutput();
            if (followAssistant.hasToolCalls() && !followAssistant.getToolCalls().isEmpty()) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
                return terminalOutcome(
                        FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                        "",
                        Map.of(),
                        Optional.empty(),
                        stages,
                        List.of("tool_call_in_follow_up"));
            }

            String finalText = followAssistant.getText();
            if (finalText == null) {
                finalText = "";
            }
            stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_SUCCESS));
            return new FunctionCallingExecutionResult(
                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                    true,
                    Optional.of(kind),
                    finalText,
                    payload,
                    List.of("fc_success"),
                    true,
                    stages);
        } catch (RuntimeException e) {
            stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
            return terminalOutcome(
                    FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                    "",
                    Map.of("error", "fc_executor_exception", "message", String.valueOf(e.getMessage())),
                    Optional.empty(),
                    stages,
                    List.of(e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
        }
    }

    private static ExecutionStageTrace fcResultMapStage(FunctionCallingOutcome o) {
        return new ExecutionStageTrace(
                "function_calling_result_map",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "outcome=" + o);
    }

    private static FunctionCallingExecutionResult terminalOutcome(
            FunctionCallingOutcome outcome,
            String answerText,
            Map<String, Object> normalizedPayload,
            Optional<DeterministicToolKind> toolKind,
            List<ExecutionStageTrace> stages,
            List<String> notes) {
        return new FunctionCallingExecutionResult(
                outcome,
                false,
                toolKind,
                answerText != null ? answerText : "",
                normalizedPayload,
                notes,
                false,
                stages);
    }
}
