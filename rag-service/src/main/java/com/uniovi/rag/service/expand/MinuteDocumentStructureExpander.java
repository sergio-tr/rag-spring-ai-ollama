package com.uniovi.rag.service.expand;

import com.uniovi.rag.model.ExpansionStrategy;
import org.springframework.ai.chat.client.ChatClient;

import java.util.regex.Pattern;

/**
 * Query expander for meeting minutes retrieval, aligned with findings from
 * "Query Expansion by Prompting Large Language Models" (Jagerman et al.): use of
 * Chain-of-Thought (CoT) or keyword expansion (Q2E), concatenation with the original
 * query (and optional upweighting by repeating it) instead of replacing it.
 */
public class MinuteDocumentStructureExpander extends AbstractQueryExpander {

    /** Default max length of the expansion segment (configurable via rag.expansion.max-expansion-chars). */
    private static final int DEFAULT_MAX_EXPANSION_LENGTH = 350;
    /** Default max total query length (configurable via rag.expansion.max-query-total-chars). */
    private static final int DEFAULT_MAX_QUERY_LENGTH_TOTAL = 512;
    /** Default max query length sent to the LLM to avoid GGML errors (configurable via rag.expansion.max-query-length-for-llm). */
    private static final int DEFAULT_MAX_QUERY_LENGTH_FOR_LLM = 500;
    /** Default query length used when retrying after a 500/GGML error (configurable via rag.expansion.retry-query-length). */
    private static final int DEFAULT_RETRY_QUERY_LENGTH = 200;

    /** Sentences/phrases to strip from CoT output before using as expansion (paper: "The final answer:", etc.). */
    private static final Pattern COT_FINAL_ANSWER_PATTERN = Pattern.compile(
            "(?i)(\\s*(?:the\\s+final\\s+answer|so\\s+the\\s+final\\s+answer is|la\\s+respuesta\\s+final|así que\\s+la\\s+respuesta\\s+es)\\s*:?\\s*[^.]*\\.?)\\s*$"
    );    

    private final ExpansionStrategy strategy;
    private final int originalQueryRepeatCount;
    private final int maxExpansionLength;
    private final int maxQueryLengthTotal;
    private final int maxQueryLengthForLlm;
    private final int retryQueryLength;

    public MinuteDocumentStructureExpander(ChatClient client) {
        this(client, ExpansionStrategy.COT, 1, DEFAULT_MAX_EXPANSION_LENGTH, DEFAULT_MAX_QUERY_LENGTH_TOTAL, DEFAULT_MAX_QUERY_LENGTH_FOR_LLM, DEFAULT_RETRY_QUERY_LENGTH);
    }

    public MinuteDocumentStructureExpander(ChatClient client, ExpansionStrategy strategy, int originalQueryRepeatCount) {
        this(client, strategy, originalQueryRepeatCount, DEFAULT_MAX_EXPANSION_LENGTH, DEFAULT_MAX_QUERY_LENGTH_TOTAL, DEFAULT_MAX_QUERY_LENGTH_FOR_LLM, DEFAULT_RETRY_QUERY_LENGTH);
    }

    public MinuteDocumentStructureExpander(ChatClient client, ExpansionStrategy strategy, int originalQueryRepeatCount,
                                          int maxExpansionLength, int maxQueryLengthTotal) {
        this(client, strategy, originalQueryRepeatCount, maxExpansionLength, maxQueryLengthTotal, DEFAULT_MAX_QUERY_LENGTH_FOR_LLM, DEFAULT_RETRY_QUERY_LENGTH);
    }

    /** Full constructor with all expansion params (used by RagConfiguration and EvaluationServiceFactory from properties). */
    public MinuteDocumentStructureExpander(ChatClient client, ExpansionStrategy strategy, int originalQueryRepeatCount,
                                          int maxExpansionLength, int maxQueryLengthTotal,
                                          int maxQueryLengthForLlm, int retryQueryLength) {
        super(client);
        this.strategy = strategy != null ? strategy : ExpansionStrategy.COT;
        this.originalQueryRepeatCount = Math.max(1, Math.min(5, originalQueryRepeatCount));
        this.maxExpansionLength = maxExpansionLength > 0 ? maxExpansionLength : DEFAULT_MAX_EXPANSION_LENGTH;
        this.maxQueryLengthTotal = maxQueryLengthTotal > 0 ? maxQueryLengthTotal : DEFAULT_MAX_QUERY_LENGTH_TOTAL;
        this.maxQueryLengthForLlm = maxQueryLengthForLlm > 0 ? maxQueryLengthForLlm : DEFAULT_MAX_QUERY_LENGTH_FOR_LLM;
        this.retryQueryLength = retryQueryLength > 0 ? retryQueryLength : DEFAULT_RETRY_QUERY_LENGTH;
    }

    private static final String DOCUMENT_STRUCTURE_REPHRASE_PROMPT = """
        You are a query enhancement system for meeting minutes retrieval.
        
        Your task is to rephrase the user's question to make it clearer, more structured, and more relevant 
        within the context of homeowners' association meeting minutes.
        
        These meeting minutes follow a formal structure with sections such as:
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions.
        - Questions and Requests: open interventions at the end of the session.
        
        IMPORTANT REQUIREMENTS:
        1. Maintain the EXACT SAME LANGUAGE as the original question. If the question is in Spanish, you MUST respond only in Spanish. Do not translate or switch to English or any other language.
        2. Keep the original meaning and intent of the question.
        3. PRESERVE THE TYPE OF QUESTION: if the user asks to summarize a topic, summarize a meeting, find a paragraph, count and explain, confirm presence, or get a specific field (date, president, etc.), do not change that intent. Add only terms that help retrieval for the SAME intent. Do not add generic keywords that could make the query look like a different type (e.g. do not turn a "summarize topic X" into a list of unrelated section terms).
        4. Use terminology from the meeting minutes structure when appropriate, but only when it supports the same question type.
        5. Do not answer the question - only rephrase it.
        
        Rephrase the question taking this structure into account, using specific terms from the sections mentioned above
        that help locate the same kind of answer the user is asking for, while keeping the user's wording.
        
        If the question cannot be rephrased because it is already well-formulated, simply return the original question unchanged.
        
        Original Question: "%s"
        
        Rephrased question (in the same language):
        """;

    /** CoT prompt: rationale step-by-step produces many keywords (paper: best for recall). Domain-adapted for actas. */
    private static final String COT_PROMPT = """
        You are helping to expand a search query for finding information in homeowners' association meeting minutes.
        
        The minutes contain: date, location, times, attendees (names, roles), agenda (topics, agreements, decisions, votes), and questions/requests.
        
        Task: Answer the following query as if you were explaining step-by-step what information is needed to answer it. 
        Give the rationale before giving any final answer. Use the SAME LANGUAGE as the query (if the query is in Spanish, write ONLY in Spanish).
        Do NOT end with a single "final answer" line - focus on the reasoning and key concepts (dates, people, topics, types of data).
        PRESERVE THE QUESTION TYPE: if the user asks to summarize a topic, summarize a meeting, find a paragraph, count and explain, or get a specific fact, keep that intent. Include only keywords that help find documents for that same intent. Do not add generic terms that could change how the query is classified.
        Your explanation will be used as extra search terms, so include relevant keywords and phrases that might appear in the minutes.
        
        Query: "%s"
        
        Rationale (same language as query):
        """;

    /** Q2E-style: list of keywords only (paper: Q2E prompts). */
    private static final String Q2E_KEYWORDS_PROMPT = """
        You are a query expansion system for meeting minutes retrieval.
        
        The minutes contain: date, location, start/end time, attendees (names, roles), agenda (topics, agreements, decisions), questions and requests.
        
        Task: Write a short list of keywords or short phrases that would help find meeting minutes answering this query.
        Use the SAME LANGUAGE as the query (if the query is in Spanish, output ONLY Spanish keywords). Output ONLY the keywords or phrases, separated by commas or newlines.
        PRESERVE THE QUESTION TYPE: do not add keywords that would change the intent (e.g. for "summarize topic X" include topic-related terms, not generic section names that suggest a different question type). Include: dates or time references if relevant, role names (president, secretary), and topic terms that might appear in the minutes and support the same question.
        Do not write full sentences.
        
        Query: "%s"
        
        Keywords (same language, comma or newline separated):
        """;

    @Override
    public String expand(String query) {
        if (query == null || query.trim().isEmpty()) {
            log().debug("Empty query provided to expander, returning original");
            return query != null ? query : "";
        }
        String original = query.trim();
        String expansion = runExpansionWithRetry(original);

        if (expansion == null || expansion.isBlank()) {
            log().debug("Expansion empty or failed, returning original query only");
            return buildResult(original, "");
        }

        expansion = truncateExpansion(expansion);
        String result = buildResult(original, expansion);
        result = capTotalLength(original, result);

        if (!isValidExpansion(original, expansion, result)) {
            log().debug("Expansion failed quality validation, returning original query only");
            return original;
        }

        log().info("-----------------------------------------------------------------------------");
        log().info("EXPANDER: strategy={}, original: {}", strategy, original);
        log().info("EXPANDER: expansion: {}", expansion);
        log().info("EXPANDER: final query (first 200 chars): {}", result.length() > 200 ? result.substring(0, 200) + "..." : result);

        return result;
    }

    /** Ensures total length does not exceed maxQueryLengthTotal to avoid context/embedding truncation. */
    private String capTotalLength(String original, String result) {
        if (result == null || result.length() <= maxQueryLengthTotal) return result;
        int prefixLen = original == null ? 0 : originalQueryRepeatCount * original.length() + Math.max(0, originalQueryRepeatCount - 1);
        if (prefixLen >= maxQueryLengthTotal) return original != null ? original : result.substring(0, maxQueryLengthTotal);
        String expansionPart = result.length() > prefixLen ? result.substring(prefixLen).trim() : "";
        int maxExpansion = maxQueryLengthTotal - prefixLen - 1;
        if (maxExpansion <= 0 || expansionPart.isEmpty()) return result.substring(0, Math.min(result.length(), maxQueryLengthTotal));
        if (expansionPart.length() <= maxExpansion) return result;
        log().debug("Capping total query length to {} (expansion truncated from {} to {} chars)",
                maxQueryLengthTotal, expansionPart.length(), maxExpansion);
        return result.substring(0, prefixLen) + " " + expansionPart.substring(0, maxExpansion).trim();
    }

    /** Builds final query: upweight original by repeating it, then append expansion (paper: Concat(q,q,q,q,q, LLM)). */
    private String buildResult(String original, String expansion) {
        if (expansion == null || expansion.isBlank()) {
            return original;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < originalQueryRepeatCount; i++) {
            if (i > 0) sb.append(" ");
            sb.append(original);
        }
        sb.append(" ").append(expansion.trim());
        return sb.toString().trim();
    }

    /**
     * Runs expansion with bounded query length and one retry on Ollama 500/GGML errors.
     * Limits input length to avoid GGML_ASSERT(ggml_can_repeat) and similar internal errors.
     */
    private String runExpansionWithRetry(String original) {
        for (int attempt = 0; attempt < 2; attempt++) {
            int maxLen = attempt == 0 ? maxQueryLengthForLlm : retryQueryLength;
            String queryForLlm = truncateForLlm(original, maxLen);
            try {
                switch (strategy) {
                    case COT:
                        return callAndPostProcessCoT(queryForLlm);
                    case Q2E:
                        return callAndExtractKeywords(queryForLlm);
                    default:
                        return callRephrase(queryForLlm);
                }
            } catch (RuntimeException e) {
                if (isRetryableOllamaError(e) && attempt == 0) {
                    log().warn("Expander LLM error (will retry with shorter query): {}", e.getMessage());
                } else {
                    log().error("Error calling LLM in expander", e);
                    return "";
                }
            }
        }
        return "";
    }

    private static String truncateForLlm(String query, int maxLen) {
        if (query == null || query.length() <= maxLen) return query != null ? query : "";
        return query.substring(0, maxLen).trim() + (query.length() > maxLen ? "…" : "");
    }

    private static boolean isRetryableOllamaError(RuntimeException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("500") || msg.contains("Internal Server Error")
                || msg.contains("GGML_ASSERT") || msg.contains("ggml_can_repeat"));
    }

    private String callRephrase(String query) {
        String prompt = String.format(DOCUMENT_STRUCTURE_REPHRASE_PROMPT, query);
        return callLlm(prompt);
    }

    private String callAndPostProcessCoT(String query) {
        String prompt = String.format(COT_PROMPT, query);
        String raw = callLlm(prompt);
        if (raw == null || raw.isBlank()) return "";
        String filtered = COT_FINAL_ANSWER_PATTERN.matcher(raw.trim()).replaceFirst("").trim();
        return filtered.isEmpty() ? raw.trim() : filtered;
    }

    private String callAndExtractKeywords(String query) {
        String prompt = String.format(Q2E_KEYWORDS_PROMPT, query);
        String raw = callLlm(prompt);
        if (raw == null || raw.isBlank()) return "";
        return raw.trim().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String callLlm(String prompt) {
        try {
            String content = client.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return content != null ? content.trim() : "";
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    private String truncateExpansion(String expansion) {
        if (expansion == null || expansion.length() <= maxExpansionLength) {
            return expansion;
        }
        String truncated = expansion.substring(0, maxExpansionLength).trim();
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > maxExpansionLength / 2) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + " ";
    }

    /**
     * Validates the expansion segment and the final result.
     * Original is always kept in result, so we validate expansion language and result size.
     */
    private boolean isValidExpansion(String original, String expansion, String fullResult) {
        if (fullResult == null || fullResult.isBlank()) {
            return false;
        }
        if (!fullResult.contains(original)) {
            log().warn("Expander result does not contain original query");
            return false;
        }

        if (expansion != null && !expansion.isBlank()) {
            boolean originalHasSpanish = original.matches(".*[áéíóúñ¿¡].*");
            boolean expansionHasSpanish = expansion.matches(".*[áéíóúñ¿¡].*");
            if (originalHasSpanish && !expansionHasSpanish) {
                log().debug("Expansion changed language from Spanish to another; using original query only");
                return false;
            }
        }

        if (fullResult.length() > original.length() * 5) {
            log().debug("Expanded query very long (original: {}, result: {})", original.length(), fullResult.length());
        }

        if (expansion != null && !expansion.isBlank()) {
            String[] originalWords = original.toLowerCase().split("\\s+");
            String expansionLower = expansion.toLowerCase();
            int matchingWords = 0;
            for (String word : originalWords) {
                if (word.length() > 3 && expansionLower.contains(word)) {
                    matchingWords++;
                }
            }
            if (originalWords.length > 0 && matchingWords < originalWords.length * 0.2) {
                log().debug("Expansion has very few matching words with original ({} out of {})", matchingWords, originalWords.length);
            }
        }

        return true;
    }
}
