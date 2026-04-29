package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.ToolResult;
import java.util.Map;

/**
 * Extends {@link MeetingMinutesToolsAdapter} and adds tracing/metrics to {@link #execute(QueryType, String)}.
 * When ObservabilitySupport is null, delegates without tracing.
 */
public class TracedMeetingMinutesToolsAdapter extends MeetingMinutesToolsAdapter {

    private static final int MAX_ATTR = 500;

    private final ObservabilitySupport observability;

    public TracedMeetingMinutesToolsAdapter(
            RagToolsConfiguration toolsConfig,
            QueryAnalyser analyser,
            ObservabilitySupport observability) {
        super(toolsConfig, analyser);
        this.observability = observability;
    }

    @Override
    public ToolResult execute(QueryType queryType, String query) {
        if (observability == null) {
            return super.execute(queryType, query);
        }
        observability.recordCounter("rag.tool.adapter.calls", "operation", "execute");
        return observability.recordTimer("rag.tool.adapter.execute", () ->
                observability.runWithSpan(
                        "rag.tool.adapter.execute",
                        Map.of(
                                "queryType", queryType != null ? queryType.name() : "null",
                                "query", truncate(query != null ? query : "")
                        ),
                        (String) null,
                        () -> super.execute(queryType, query)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
