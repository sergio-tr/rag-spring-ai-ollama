package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;

/**
 * Result of the pre-generation phase: expanded text, optional NER, optional query type.
 */
public record PreparedQuery(String expandedQuery, JSONObject nerEntities, QueryType queryType) {
}
