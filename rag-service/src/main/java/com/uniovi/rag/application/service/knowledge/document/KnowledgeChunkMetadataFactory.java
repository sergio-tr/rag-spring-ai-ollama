package com.uniovi.rag.application.service.knowledge.document;

import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimum chunk metadata for {@code vector_store.metadata}, camelCase keys.
 */
public final class KnowledgeChunkMetadataFactory {

    private KnowledgeChunkMetadataFactory() {
    }

    public static Map<String, Object> buildV2(
            CorpusScope corpusScope,
            UUID documentId,
            UUID projectId,
            UUID conversationId,
            UUID indexSnapshotId,
            String indexSignatureHashHex,
            String filename,
            int chunkIndex,
            int totalChunks,
            String documentIdHash) {
        return buildV2(
                corpusScope,
                documentId,
                projectId,
                conversationId,
                indexSnapshotId,
                indexSignatureHashHex,
                filename,
                chunkIndex,
                totalChunks,
                documentIdHash,
                false);
    }

    public static Map<String, Object> buildV2(
            CorpusScope corpusScope,
            UUID documentId,
            UUID projectId,
            UUID conversationId,
            UUID indexSnapshotId,
            String indexSignatureHashHex,
            String filename,
            int chunkIndex,
            int totalChunks,
            String documentIdHash,
            boolean truncatedForEmbed) {
        Map<String, Object> m = new HashMap<>();
        m.put("corpusScope", corpusScope.name());
        m.put("documentId", documentId.toString());
        m.put("sourceDocumentId", documentId.toString());
        if (projectId != null) {
            m.put("projectId", projectId.toString());
        }
        if (conversationId != null) {
            m.put("conversationId", conversationId.toString());
        }
        if (indexSnapshotId != null) {
            m.put("indexSnapshotId", indexSnapshotId.toString());
        }
        m.put("indexSignatureHash", indexSignatureHashHex);
        m.put("filename", filename != null ? filename : "unknown");
        m.put("chunkIndex", chunkIndex);
        m.put("totalChunks", totalChunks);
        m.put("projectDocumentId", documentId.toString());
        if (truncatedForEmbed) {
            m.put("truncated", true);
        }
        if (documentIdHash != null) {
            m.put("document_id", documentIdHash);
        }
        return m;
    }

    public static String contentHashId(String filename, String content, UUID projectDocumentId) {
        String base = (filename != null ? filename : "unknown") + "_" + projectDocumentId + "_"
                + (content != null ? String.valueOf(content.hashCode()) : "0");
        return Integer.toUnsignedString(base.hashCode());
    }

    private static final Set<String> ACTA_SCALAR_KEYS =
            Set.of(
                    "document_id",
                    "id",
                    "filename",
                    "sourceTitle",
                    "documentTitle",
                    "actaDate",
                    "sectionType",
                    "sectionPart",
                    "date",
                    "date_iso",
                    "place",
                    "startTime",
                    "endTime",
                    "durationMinutes",
                    "president",
                    "secretary",
                    "numberOfAttendees",
                    "attendeesCount",
                    "year",
                    "month",
                    "summary",
                    "agenda_raw",
                    "minute");

    private static final Set<String> ACTA_LIST_KEYS =
            Set.of("attendees", "topics", "decisions", "mentionedEntities", "sections", "budgetMentions", "namedPeople");

    /**
     * Copies structured acta fields into vector-store chunk metadata for retrieval and tools.
     */
    public static void mergeActaStructuredFields(Map<String, Object> target, Map<String, Object> acta) {
        if (target == null || acta == null || acta.isEmpty()) {
            return;
        }
        for (String key : ACTA_SCALAR_KEYS) {
            Object value = acta.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
        for (String key : ACTA_LIST_KEYS) {
            Object value = acta.get(key);
            if (value instanceof List<?> list && !list.isEmpty()) {
                target.put(key, value);
            }
        }
        Object agenda = acta.get("agenda");
        if (agenda instanceof Map<?, ?> map && !map.isEmpty()) {
            target.put("agenda", agenda);
        }
        Object fieldPresence = acta.get("fieldPresence");
        if (fieldPresence instanceof Map<?, ?> presence && !presence.isEmpty()) {
            target.put("fieldPresence", presence);
        }
    }

    /** Short prefix so embeddings retain date/president even when split across chunks. */
    public static String buildActaEmbeddingPrefix(Map<String, Object> acta) {
        if (acta == null || acta.isEmpty()) {
            return "";
        }
        Object dateObj = acta.get("date_iso") != null ? acta.get("date_iso") : acta.get("date");
        Object presidentObj = acta.get("president");
        String dateStr = dateObj != null ? dateObj.toString().trim() : "";
        String presidentStr = presidentObj != null ? presidentObj.toString().trim() : "";
        if (dateStr.isEmpty() && presidentStr.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!dateStr.isEmpty()) {
            sb.append("Acta ").append(dateStr).append(". ");
        }
        if (!presidentStr.isEmpty()) {
            sb.append("Presidente: ").append(presidentStr).append(". ");
        }
        return sb.toString();
    }

    /**
     * Section-aware chunk metadata for acta indexing and retrieval expansion.
     */
    public static void mergeSectionFields(
            Map<String, Object> target,
            ActaSectionChunk section,
            Map<String, Object> acta,
            String documentTitle) {
        if (target == null) {
            return;
        }
        if (documentTitle != null && !documentTitle.isBlank()) {
            target.put("documentTitle", documentTitle);
            if (!target.containsKey("filename")) {
                target.put("filename", documentTitle);
            }
        }
        if (section != null) {
            target.put("sectionType", section.sectionType());
            if (section.sectionPart() > 0) {
                target.put("sectionPart", section.sectionPart());
            }
        }
        if (acta != null && !acta.isEmpty()) {
            Object actaDate = acta.get("date_iso") != null ? acta.get("date_iso") : acta.get("date");
            if (actaDate != null) {
                target.put("actaDate", actaDate);
            }
            mergeActaStructuredFields(target, acta);
        }
    }
}
