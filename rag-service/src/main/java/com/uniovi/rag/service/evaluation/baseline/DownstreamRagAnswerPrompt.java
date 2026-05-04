package com.uniovi.rag.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;

/** Builds the user turn for embedding-downstream RAG (retrieved chunks → fixed LLM). */
public final class DownstreamRagAnswerPrompt {

    private DownstreamRagAnswerPrompt() {}

    public static String userTurn(String query, List<Document> docs, PromptProfileSnapshot prompts) {
        String q = query != null ? query : "";
        String template =
                prompts.retrievalQuestionTemplate() != null && !prompts.retrievalQuestionTemplate().isBlank()
                        ? prompts.retrievalQuestionTemplate()
                        : EvaluationBaselinePrompts.RETRIEVAL_QUESTION_TEMPLATE;
        String blocks =
                docs == null || docs.isEmpty()
                        ? "(no chunks retrieved)"
                        : docs.stream()
                                .map(DownstreamRagAnswerPrompt::docBlock)
                                .collect(Collectors.joining("\n\n"));
        return template.replace("{{question}}", q)
                + "\n\nDOCUMENT CONTEXT:\n"
                + blocks
                + "\n\n"
                + (prompts.answerFormatting() != null ? prompts.answerFormatting() : "");
    }

    private static String docBlock(Document d) {
        String id = d.getId() != null ? d.getId() : "?";
        String text = d.getText() != null ? d.getText() : "";
        return "--- chunk id=" + id + " ---\n" + text;
    }
}
