package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetTemplateFactory;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExperimentalDatasetLabServiceUploadValidationTest {

    @Test
    void upload_templateOnlyWorkbook_rejectedWithDatasetTooSmall() throws Exception {
        EvaluationDatasetRepository repo = mock(EvaluationDatasetRepository.class);
        UserRepository users = mock(UserRepository.class);
        EvaluationDatasetStorePort store = mock(EvaluationDatasetStorePort.class);
        EvaluationWorkbookParser parser = new EvaluationWorkbookParser();
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(parser);

        ExperimentalDatasetLabService svc = new ExperimentalDatasetLabService(repo, users, store, parser, loader);

        byte[] template = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.RAG_PRESET_BENCHMARK);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                template);

        assertThatThrownBy(() -> svc.upload(UUID.randomUUID(), file, "rag-preset-benchmark", "n", null))
                .isInstanceOf(ExperimentalDatasetValidationException.class)
                .satisfies(ex -> {
                    ExperimentalDatasetValidationException vex = (ExperimentalDatasetValidationException) ex;
                    Set<ValidationIssueCode> codes = vex.validationReport().issues().stream()
                            .map(i -> i.code())
                            .collect(Collectors.toSet());
                    assertThat(codes).contains(ValidationIssueCode.DATASET_TOO_SMALL);
                });
    }
}

