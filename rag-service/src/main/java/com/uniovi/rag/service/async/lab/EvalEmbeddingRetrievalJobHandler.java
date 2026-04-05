package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retrieval-only benchmark: checks whether expected answer text appears in top-k vector hits (proxy for recall@k).
 */
@Component
class EvalEmbeddingRetrievalJobHandler implements LabJobHandler {

    private final PgVectorStore vectorStore;
    private final EvaluationService evaluationService;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final int topK;

    EvalEmbeddingRetrievalJobHandler(
            PgVectorStore vectorStore,
            EvaluationService evaluationService,
            EvaluationCanonicalPersistenceService canonicalPersistence,
            @Value("${spring.ai.ollama.top-k:5}") int topK) {
        this.vectorStore = vectorStore;
        this.evaluationService = evaluationService;
        this.canonicalPersistence = canonicalPersistence;
        this.topK = Math.max(1, topK);
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            mutation.appendProgressLine(taskId, "Embedding retrieval benchmark (vector search)…");
            Map<String, String> qa = evaluationService.getQuestionsAndAnswers();
            List<Map<String, Object>> rows = new ArrayList<>();
            int hitsAt1 = 0;
            int hitsAtK = 0;
            int n = 0;
            for (Map.Entry<String, String> e : qa.entrySet()) {
                String question = e.getKey() != null ? e.getKey() : "";
                String expected = e.getValue() != null ? e.getValue() : "";
                long t0 = System.nanoTime();
                SearchRequest req =
                        SearchRequest.builder().query(question).topK(topK).similarityThreshold(0.0).build();
                List<Document> docs = vectorStore.similaritySearch(req);
                long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
                boolean at1 = !docs.isEmpty() && containsExpected(docs.get(0), expected);
                boolean atK = docs.stream().anyMatch(d -> containsExpected(d, expected));
                if (at1) {
                    hitsAt1++;
                }
                if (atK) {
                    hitsAtK++;
                }
                n++;
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("recall_at_1", at1 ? 1.0 : 0.0);
                metrics.put("recall_at_k", atK ? 1.0 : 0.0);
                metrics.put("k", topK);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("question", question);
                row.put("expected_answer", expected);
                row.put("top_document_id", docs.isEmpty() ? null : docs.get(0).getId());
                row.put("latency_ms", latencyMs);
                row.put("metrics", metrics);
                rows.add(row);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            Map<String, Object> retrieval = new LinkedHashMap<>();
            retrieval.put("mean_recall_at_1", n > 0 ? (double) hitsAt1 / n : null);
            retrieval.put("mean_recall_at_k", n > 0 ? (double) hitsAtK / n : null);
            retrieval.put("n", n);
            retrieval.put("k", topK);
            summary.put("retrieval", retrieval);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("results", rows);
            payload.put("evaluation_summary", summary);
            if (evaluationRunId != null) {
                canonicalPersistence.persistEmbeddingRetrievalResults(evaluationRunId, payload);
            }
            mutation.markSucceeded(taskId, payload);
        } catch (RuntimeException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    private static boolean containsExpected(Document doc, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String text = doc.getText();
        if (text == null) {
            return false;
        }
        String needle = expected.length() > 120 ? expected.substring(0, 120).trim() : expected.trim();
        return text.toLowerCase().contains(needle.toLowerCase());
    }
}
