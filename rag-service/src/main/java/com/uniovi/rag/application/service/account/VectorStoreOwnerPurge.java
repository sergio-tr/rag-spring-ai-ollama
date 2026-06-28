package com.uniovi.rag.application.service.account;

import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deletes vector_store rows owned by a user via project document ids or project ids.
 */
@Component
public class VectorStoreOwnerPurge {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreOwnerPurge(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int purgeForDocumentIds(Collection<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID documentId : documentIds) {
            String id = documentId.toString();
            total +=
                    jdbcTemplate.update(
                            """
                            DELETE FROM vector_store
                            WHERE metadata->>'projectDocumentId' = ?
                               OR metadata->>'documentId' = ?
                               OR metadata->>'document_id' = ?
                            """,
                            id,
                            id,
                            id);
        }
        return total;
    }

    public int purgeForProjectIds(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID projectId : projectIds) {
            total += jdbcTemplate.update("DELETE FROM vector_store WHERE project_id = ?", projectId);
        }
        return total;
    }
}
