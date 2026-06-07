package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionWatchdogTest {

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @InjectMocks
    private DocumentIngestionWatchdog watchdog;

    @Test
    void markStalledIngestsAsError_marksOldIngestingRows() {
        KnowledgeDocumentEntity stale = mock(KnowledgeDocumentEntity.class);
        when(stale.getReindexedAt()).thenReturn(Instant.now().minus(10, ChronoUnit.MINUTES));

        KnowledgeDocumentEntity fresh = mock(KnowledgeDocumentEntity.class);
        when(fresh.getUploadedAt()).thenReturn(Instant.now());

        when(knowledgeDocumentRepository.findByStatus(ProjectDocumentStatus.INGESTING))
                .thenReturn(List.of(stale, fresh));

        watchdog.markStalledIngestsAsError();

        verify(stale).setStatus(ProjectDocumentStatus.ERROR);
        verify(stale)
                .setErrorMessage(
                        argThat(
                                msg -> msg != null && msg.contains(DocumentIngestionFailureCodes.FAILED_STALE_INGESTION)));
        verify(knowledgeDocumentRepository).save(stale);
        verify(knowledgeDocumentRepository, never()).save(fresh);
    }

    @Test
    void markStalledIngestsAsError_usesUploadedAtWhenReindexedAtMissing() {
        KnowledgeDocumentEntity stale = mock(KnowledgeDocumentEntity.class);
        when(stale.getReindexedAt()).thenReturn(null);
        when(stale.getUploadedAt()).thenReturn(Instant.now().minus(15, ChronoUnit.MINUTES));

        when(knowledgeDocumentRepository.findByStatus(ProjectDocumentStatus.INGESTING))
                .thenReturn(List.of(stale));

        watchdog.markStalledIngestsAsError();

        verify(stale).setStatus(ProjectDocumentStatus.ERROR);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(stale).setErrorMessage(message.capture());
        assertThat(message.getValue()).contains(DocumentIngestionFailureCodes.FAILED_STALE_INGESTION);
    }
}
