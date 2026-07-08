package com.uniovi.rag.application.service.runtime.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;

/** Test doubles for workflow unit tests migrated off {@code ChatClient}. */
public final class RagLlmChatInvokerTestSupport {

    private RagLlmChatInvokerTestSupport() {}

    public static RagLlmChatInvoker stubContent(String content) {
        RagLlmChatInvoker invoker = mock(RagLlmChatInvoker.class);
        when(invoker.invoke(any(ExecutionContext.class), anyString(), anyString()))
                .thenReturn(content != null ? content : "");
        return invoker;
    }

    /** Stubs {@link TaskLlmConfigResolver#resolveFinalAnswer} to pass through the orchestration base config. */
    public static TaskLlmConfigResolver passthroughFinalAnswerResolver() {
        TaskLlmConfigResolver resolver = mock(TaskLlmConfigResolver.class);
        lenient()
                .when(resolver.resolveFinalAnswer(any(), any()))
                .thenAnswer(
                        inv -> {
                            ResolvedLlmConfig base = inv.getArgument(1);
                            return new TaskLlmConfigResolver.FinalAnswerCallConfig(
                                    base,
                                    base.chatModel(),
                                    base.temperature(),
                                    true,
                                    true,
                                    false,
                                    "primary_inherited",
                                    "primary_inherited");
                        });
        return resolver;
    }
}
