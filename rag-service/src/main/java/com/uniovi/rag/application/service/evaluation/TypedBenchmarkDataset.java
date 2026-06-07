package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.workbook.CorpusDocument;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalDataset;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;

import java.util.List;

/** Resolved typed payload for a canonical benchmark run (after parsing storageUri / classpath). */
public sealed interface TypedBenchmarkDataset {

    record LlmQuestions(List<LlmReaderQuestion> questions, List<CorpusDocument> corpusDocuments)
            implements TypedBenchmarkDataset {

        public LlmQuestions {
            questions = List.copyOf(questions);
            corpusDocuments = corpusDocuments == null ? List.of() : List.copyOf(corpusDocuments);
        }
    }

    record EmbeddingQuestions(EmbeddingRetrievalDataset dataset) implements TypedBenchmarkDataset {}

    record RagPresetQuestions(List<RagPresetQuestion> questions, List<RagPresetDefinition> presetCatalog)
            implements TypedBenchmarkDataset {

        public RagPresetQuestions {
            questions = List.copyOf(questions);
            presetCatalog = presetCatalog == null ? List.of() : List.copyOf(presetCatalog);
        }
    }
}
