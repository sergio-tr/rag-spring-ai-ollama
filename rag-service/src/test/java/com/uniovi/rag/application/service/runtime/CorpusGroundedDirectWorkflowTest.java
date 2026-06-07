package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.Mockito.mock;

class CorpusGroundedDirectWorkflowTest {

    @Test
    void workflowName_isStable_forLabMetrics() {
        CorpusGroundedDirectWorkflow wf =
                new CorpusGroundedDirectWorkflow(
                        mock(ChatClient.class),
                        mock(SnapshotCorpusAssembler.class),
                        new RuntimePromptBudgeter(new RagRuntimeProperties()),
                        null);
        assertThat(wf.workflowName()).isEqualTo("CorpusGroundedDirectWorkflow");
    }
}
