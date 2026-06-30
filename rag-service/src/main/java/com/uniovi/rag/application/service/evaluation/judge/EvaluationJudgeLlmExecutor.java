package com.uniovi.rag.application.service.evaluation.judge;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provider-aware evaluation judge LLM invocations (BL-009). Does not use the Spring AI default {@code ChatClient}
 * bean (often Ollama {@code gemma3:4b}).
 */
@Service
public class EvaluationJudgeLlmExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationJudgeLlmExecutor.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver configResolver;
    private final TaskLlmConfigResolver taskLlmConfigResolver;
    private final LabBenchmarkDefaultModelResolver defaultModelResolver;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ConfigurablePromptResolver promptResolver;
    private final String configuredJudgeChatModel;

    public EvaluationJudgeLlmExecutor(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver configResolver,
            TaskLlmConfigResolver taskLlmConfigResolver,
            LabBenchmarkDefaultModelResolver defaultModelResolver,
            EvaluationRunRepository evaluationRunRepository,
            ConfigurablePromptResolver promptResolver,
            @Value("${rag.evaluation.judge.chat-model:}") String configuredJudgeChatModel) {
        this.llmClientResolver = llmClientResolver;
        this.configResolver = configResolver;
        this.taskLlmConfigResolver = taskLlmConfigResolver;
        this.defaultModelResolver = defaultModelResolver;
        this.evaluationRunRepository = evaluationRunRepository;
        this.promptResolver = promptResolver;
        this.configuredJudgeChatModel = blankToNull(configuredJudgeChatModel);
    }

    public String buildJudgeUserPrompt(String question, String correctAnswer, String generatedAnswer) {
        UUID userId = resolveUserId();
        String template = promptResolver.resolve(ConfigurablePromptGroup.EVALUATION_JUDGE, userId, null);
        return template
                .replace("{question}", question != null ? question : "")
                .replace("{correctAnswer}", correctAnswer != null ? correctAnswer : "")
                .replace("{generatedAnswer}", generatedAnswer != null ? generatedAnswer : "");
    }

    public String completeJudgeUserPrompt(String userPrompt) {
        UUID userId = resolveUserId();
        TaskLlmConfigResolver.SecondaryCallConfig call =
                taskLlmConfigResolver.resolveSecondaryCall(userId, null, "evaluation-judge", null, null);
        String judgeModel =
                call.taskOverrideApplied() && call.effectiveModel() != null && !call.effectiveModel().isBlank()
                        ? call.effectiveModel()
                        : resolveJudgeModelId(userId);
        ResolvedLlmConfig judgeConfig =
                withChatModelAndSampling(call.effectiveConfig(), judgeModel, call.effectiveTemperature());

        log.info(
                "Evaluation judge LLM call: provider={}, model={}, userId={}, taskOverride={}",
                judgeConfig.chatProvider(),
                judgeModel,
                userId,
                call.taskOverrideApplied());

        try {
            LlmChatClient client = llmClientResolver.resolveChatClient(judgeConfig);
            LlmChatResponse response =
                    client.chat(
                            LlmChatRequest.of(
                                    judgeModel,
                                    null,
                                    userPrompt,
                                    judgeConfig.temperature(),
                                    judgeConfig.timeoutMs(),
                                    judgeConfig.additionalParameters()));
            String content = response != null ? response.content() : null;
            if (content == null || content.isBlank()) {
                throw EvaluationJudgeException.emptyResponse(judgeConfig.chatProvider(), judgeModel);
            }
            return content.trim();
        } catch (EvaluationJudgeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw EvaluationJudgeException.invocationFailed(judgeConfig.chatProvider(), judgeModel, ex);
        }
    }

    String resolveJudgeModelId(UUID userId) {
        String explicit =
                EvaluationJudgeExecutionScope.currentJudgeModelOverride()
                        .or(() -> Optional.ofNullable(configuredJudgeChatModel))
                        .orElse(null);
        return defaultModelResolver.resolveLlmModelId(userId, explicit);
    }

    private UUID resolveUserId() {
        Optional<UUID> scoped = EvaluationJudgeExecutionScope.currentUserId();
        if (scoped.isPresent()) {
            return scoped.get();
        }
        return LabBenchmarkExecutionContext.currentLabRuntimeContext()
                .flatMap(ctx -> ctx.runId() != null ? evaluationRunRepository.findById(ctx.runId()) : Optional.empty())
                .map(EvaluationRunEntity::getUser)
                .map(u -> u.getId())
                .orElse(null);
    }

    private static ResolvedLlmConfig withChatModel(ResolvedLlmConfig base, String chatModel) {
        return withChatModelAndSampling(base, chatModel, base.temperature());
    }

    private static ResolvedLlmConfig withChatModelAndSampling(
            ResolvedLlmConfig base, String chatModel, Double temperature) {
        return new ResolvedLlmConfig(
                base.chatProvider(),
                base.embeddingProvider(),
                base.baseUrl(),
                chatModel,
                base.embeddingModel(),
                base.apiKeyEnv(),
                base.secretName(),
                temperature,
                base.timeoutMs(),
                base.systemPrompt(),
                base.additionalParameters());
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
