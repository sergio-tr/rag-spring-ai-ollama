package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperimentalDatasetLabServiceReferenceBundleTest {

    @Test
    void listForUser_includesReferenceBundleRow_whenClasspathBundlePresent() {
        EvaluationDatasetRepository repo = mock(EvaluationDatasetRepository.class);
        UserRepository users = mock(UserRepository.class);
        EvaluationDatasetStorePort store = mock(EvaluationDatasetStorePort.class);

        when(repo.findByOwner_IdOrderByUploadedAtDesc(UUID.fromString("00000000-0000-4000-8000-000000000001")))
                .thenReturn(List.of());

        ExperimentalDatasetLabService svc =
                new ExperimentalDatasetLabService(
                        repo,
                        users,
                        store,
                        new EvaluationWorkbookParser(),
                        new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));

        var items = svc.listForUser(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(items).isNotEmpty();
        assertThat(items.getFirst().experimentalDatasetType()).isEqualTo("REFERENCE_BUNDLE");
        assertThat(items.getFirst().readOnly()).isTrue();
        assertThat(items.getFirst().validationStatus()).isEqualTo("VALID");
        assertThat(items.getFirst().isReferenceBundle()).isTrue();
        assertThat(items.getFirst().questionCounts().llmReaderQuestions()).isGreaterThan(0);
    }
}

