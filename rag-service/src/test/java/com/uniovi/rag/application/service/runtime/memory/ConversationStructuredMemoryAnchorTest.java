package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.ChatMessageWorkService;
import com.uniovi.rag.application.service.runtime.RagExecutionMapper;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.AnswerFinality;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStructuredMemoryAnchorTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ObjectProvider<RuntimeObservability> runtimeObservability;

    @Test
    void persistsAnchoredActaDateAfterGroundedAnswer() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentId", "doc-feb-24");
        source.put("filename", "acta-2025-02-24.pdf");
        source.put("detectedDate", "2025-02-24");
        source.put("metadata", Map.of("documentTitle", "Acta reunión 24 de febrero de 2025"));
        List<Map<String, Object>> sources = List.of(source);

        ExecutionTrace trace = ExecutionTrace.placeholder();
        RagExecutionResult result =
                new RagExecutionResult(
                        "El presidente fue Ana López.",
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        trace,
                        null,
                        QueryType.GET_FIELD,
                        false,
                        List.of(),
                        Optional.empty(),
                        sources,
                        AnswerFinality.STANDARD);

        QueryResponse response = RagExecutionMapper.toQueryResponse(result);
        Map<String, Object> telemetry = response.getChatTelemetry();

        assertThat(telemetry)
                .containsEntry(ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE, "2025-02-24")
                .containsEntry(ConversationMemoryAnchorMetadata.LAST_REFERENCED_DATE, "2025-02-24")
                .containsEntry(ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_ID, "doc-feb-24")
                .containsEntry(
                        ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_TITLE,
                        "Acta reunión 24 de febrero de 2025")
                .containsEntry(
                        ConversationMemoryAnchorMetadata.TOP_SOURCE_ORIGINAL_FILE_NAME, "acta-2025-02-24.pdf")
                .containsEntry(ConversationMemoryAnchorMetadata.ANSWER_SCOPE, ConversationMemoryAnchorMetadata.SCOPE_SINGLE_SOURCE);
        assertThat(response.getAnswer()).doesNotContain("anchoredActaDate");
    }

    @Test
    void followUpUsesStructuredAnchorBeforeTextHistory() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 01/01/2020?",
                                Map.of()),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "El presidente fue Carlos Ruiz.",
                                Map.of(
                                        ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE,
                                        "2025-02-24",
                                        ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_ID,
                                        "doc-grounded")));

        assertThat(ConversationFollowUpResolver.expand(history, "¿quién fue el presidente?"))
                .get()
                .asString()
                .contains("2025-02-24")
                .doesNotContain("01/01/2020");
    }

    @Test
    void fallsBackToTextualHistoryWhenStructuredAnchorIsMissing() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quiénes asistieron al acta del 24 de febrero de 2025?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Asistieron 18 personas.",
                                Map.of()));

        assertThat(ConversationFollowUpResolver.expand(history, "¿quién fue el presidente?"))
                .get()
                .asString()
                .contains("24 de febrero de 2025");
    }

    @Test
    void doesNotExposeAnchorMetadataInUserAnswer() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity message = new MessageEntity();
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        ChatMessageWorkService service =
                new ChatMessageWorkService(messageRepository, conversationRepository, null, runtimeObservability);

        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put(ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE, "2025-02-24");
        telemetry.put(ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_ID, "doc-1");
        telemetry.put(ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_TITLE, "Acta febrero");
        telemetry.put(ConversationMemoryAnchorMetadata.TOP_SOURCE_ORIGINAL_FILE_NAME, "acta.pdf");
        telemetry.put(ConversationMemoryAnchorMetadata.ANSWER_SCOPE, ConversationMemoryAnchorMetadata.SCOPE_SINGLE_SOURCE);

        service.applyAssistantSuccess(
                assistantId,
                convId,
                "El presidente fue Ana López.",
                List.of(),
                "PLAIN",
                "trace-anchor",
                List.of(),
                "llama",
                Duration.ofMillis(10),
                telemetry);

        assertThat(message.getContent()).isEqualTo("El presidente fue Ana López.");
        assertThat(message.getContent())
                .doesNotContain("anchoredActaDate")
                .doesNotContain("topSourceDocumentId")
                .doesNotContain("answerScope");
        assertThat(message.getExecutionMetadata())
                .containsEntry(ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE, "2025-02-24")
                .containsEntry(ConversationMemoryAnchorMetadata.TOP_SOURCE_DOCUMENT_ID, "doc-1");
    }
}
