package com.uniovi.rag.service.retriever;

import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a single prompt context string by concatenating {@code vector_store} row {@code content}
 * for the active project (demo: "naive full corpus" vs semantic retrieval).
 */
@Service
public class NaiveCorpusContextService {

    private static final String SEPARATOR = "\n\n--- chunk ---\n\n";

    private final NamedParameterJdbcTemplate namedJdbc;

    public NaiveCorpusContextService(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    /**
     * When {@linkplain RagExecutionContext#resolvedConfig() resolved config} enables
     * {@code naiveFullCorpusInPromptEnabled}, returns concatenated chunk text capped by
     * {@code naiveFullCorpusMaxChars} for the scoped project (and optional document allowlist).
     */
    public String buildNaiveCorpusContextIfConfigured() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || ctx.resolvedConfig() == null || !ctx.resolvedConfig().naiveFullCorpusInPromptEnabled()) {
            return null;
        }
        if (!ctx.restrictsByProject()) {
            return null;
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(ctx.projectId().trim());
        } catch (Exception e) {
            return null;
        }
        int maxChars = Math.max(1024, ctx.resolvedConfig().naiveFullCorpusMaxChars());

        List<String> contents = fetchContents(projectId, ctx);
        if (contents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(maxChars, 65536));
        for (String c : contents) {
            if (c == null || c.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(SEPARATOR);
            }
            sb.append(c.trim());
            if (sb.length() >= maxChars) {
                sb.setLength(maxChars);
                break;
            }
        }
        String out = sb.toString().trim();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars);
        }
        return out;
    }

    private List<String> fetchContents(UUID projectId, RagExecutionContext ctx) {
        MapSqlParameterSource p = new MapSqlParameterSource("projectId", projectId);
        if (ctx.documentFilterIsAll()) {
            return namedJdbc.query(
                    """
                    SELECT content FROM vector_store
                    WHERE project_id = :projectId
                    ORDER BY COALESCE(chunk_index, 0), id
                    """,
                    p,
                    (rs, rowNum) -> rs.getString(1));
        }
        Set<String> allowed = new HashSet<>();
        for (String id : ctx.documentFilter()) {
            if (id != null && !id.isBlank() && !RagExecutionContext.ALL_DOCUMENTS.equalsIgnoreCase(id.trim())) {
                allowed.add(id.trim());
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        p.addValue("docIds", new ArrayList<>(allowed));
        return namedJdbc.query(
                """
                SELECT content FROM vector_store
                WHERE project_id = :projectId
                  AND metadata->>'document_id' IN (:docIds)
                ORDER BY COALESCE(chunk_index, 0), id
                """,
                p,
                (rs, rowNum) -> rs.getString(1));
    }
}
