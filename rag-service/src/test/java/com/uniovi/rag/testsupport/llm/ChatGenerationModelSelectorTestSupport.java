package com.uniovi.rag.testsupport.llm;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Map;
import java.util.Optional;
import org.mockito.Mockito;

/** Mockito helpers for chat model selection in legacy unit tests. */
public final class ChatGenerationModelSelectorTestSupport {

    private ChatGenerationModelSelectorTestSupport() {}

    public static ChatGenerationModelSelector permissiveMock() {
        ChatGenerationModelSelector selector = Mockito.mock(ChatGenerationModelSelector.class);
        Mockito.doAnswer(
                        invocation -> {
                            ExecutionContext ctx = invocation.getArgument(0);
                            if (ctx != null && ctx.chatModelOverride().isPresent()) {
                                return Optional.of(ctx.chatModelOverride().get().trim());
                            }
                            if (ctx != null
                                    && ctx.resolved() != null
                                    && ctx.resolved().toRagConfig() != null
                                    && ctx.resolved().toRagConfig().llmModel() != null) {
                                return Optional.of(ctx.resolved().toRagConfig().llmModel().trim());
                            }
                            return Optional.empty();
                        })
                .when(selector)
                .effectiveChatModelId(Mockito.any());
        return selector;
    }

    public static ChatGenerationModelSelector catalogBacked() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
        return new ChatGenerationModelSelector(catalog);
    }
}
