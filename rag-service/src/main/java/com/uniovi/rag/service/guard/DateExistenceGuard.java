package com.uniovi.rag.service.guard;

import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;

import java.util.Optional;

/**
 * Guard that checks whether an act exists for the requested date before invoking date-dependent tools.
 * Implementations return a standard "no acta" response when no document matches the date.
 */
public interface DateExistenceGuard extends Loggable {

    /**
     * If the query is date-dependent and no document exists for the requested date, returns a ToolResult
     * with the standard "no acta" message. Otherwise returns empty.
     */
    Optional<ToolResult> checkNoActaForDate(String query, QueryType queryType, JSONObject nerEntities);
}
