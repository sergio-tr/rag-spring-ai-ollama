package com.uniovi.rag.application.model;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationModelTest {

    @Test
    void queryResponse_fromToolAndFromLlm() {
        QueryResponse tool = QueryResponse.fromTool("a", "t", QueryType.BOOLEAN_QUERY);
        assertTrue(tool.isUsedTool());
        assertEquals("t", tool.getToolUsed());
        assertEquals(QueryType.BOOLEAN_QUERY, tool.getQueryType());

        QueryResponse llm = QueryResponse.fromLLM("b", QueryType.SUMMARIZE_TOPIC);
        assertFalse(llm.isUsedTool());
        assertNull(llm.getToolUsed());

        QueryResponse bare = QueryResponse.fromLLM("c");
        assertNull(bare.getQueryType());
    }

    @Test
    void candidateResponse_factories() {
        assertNull(CandidateResponse.of("x").source());
        assertEquals("src", CandidateResponse.of("x", "src").source());
    }

    @Test
    void postStepOutput_factories() {
        assertTrue(PostStepOutput.verified("v").verified());
        assertFalse(PostStepOutput.refined("r").verified());
    }

    @Test
    void reasoningPreOutput_factories() {
        assertNull(ReasoningPreOutput.of("t").extraContext());
        assertEquals("e", ReasoningPreOutput.of("t", "e").extraContext());
    }

    @Test
    void draftAndContext_andStreamConversationContext_compose() {
        DraftAndContext dac = new DraftAndContext("d", "ctx");
        assertEquals("d", dac.draft());
        UUID conv = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        StreamConversationContext scc = new StreamConversationContext(conv, user, project, List.of("doc"));
        assertEquals(conv, scc.conversationId());
        assertEquals(1, scc.documentFilter().size());
    }
}
