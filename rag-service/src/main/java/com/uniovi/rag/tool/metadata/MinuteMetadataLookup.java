package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.springframework.ai.document.Document;

/**
 * Direct acta lookup by {@code date_iso} - bypasses semantic ranking for terminal metadata field queries.
 */
public final class MinuteMetadataLookup {

    private MinuteMetadataLookup() {}

    /**
     * Retrieves and filters corpus documents whose metadata {@code date_iso} exactly matches the query date.
     */
    public static List<Document> byDateIso(
            AbstractMetadataTool tool, String query, JSONObject ner, String[] relevantFields) {
        return tool.lookupDocumentsByDateIso(query, ner, relevantFields);
    }
}
