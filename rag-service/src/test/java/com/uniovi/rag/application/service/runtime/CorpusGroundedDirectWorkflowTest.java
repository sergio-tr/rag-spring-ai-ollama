package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import org.junit.jupiter.api.Test;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvokerTestSupport;
import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.Mockito.mock;

class CorpusGroundedDirectWorkflowTest {

    @Test
    void workflowName_isStable_forLabMetrics() {
        CorpusGroundedDirectWorkflow wf =
                new CorpusGroundedDirectWorkflow(
                        mock(RagLlmChatInvoker.class),
                        mock(SnapshotCorpusAssembler.class),
                        new RuntimePromptBudgeter(new RagRuntimeProperties()),
                        TestConfigurablePromptResolver.answerPromptResolver(),
                        null);
        assertThat(wf.workflowName()).isEqualTo("CorpusGroundedDirectWorkflow");
    }
}
