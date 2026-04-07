package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds NER JSON for {@link com.uniovi.rag.tool.ToolExecutionContext} from {@link QueryPlan} only (no analyser).
 */
public final class QueryPlanEntitySupport {

    private QueryPlanEntitySupport() {
    }

    public static JSONObject nerFromPlan(QueryPlan plan) {
        JSONObject ner = new JSONObject();
        EntityExtractionResult e = plan.entityExtractionResult();
        if (!e.dates().isEmpty()) {
            ner.put("date", e.dates().get(0));
            ner.put("dates", String.join(",", e.dates()));
            ner.put("date_iso", e.dates().get(0));
            ner.put("fechas", String.join(",", e.dates()));
        }
        if (!e.people().isEmpty()) {
            ner.put("people", new JSONArray(e.people()));
        }
        if (!e.locations().isEmpty()) {
            ner.put("locations", new JSONArray(e.locations()));
        }
        if (!e.topics().isEmpty()) {
            ner.put("topics", new JSONArray(e.topics()));
        }
        if (!e.organizations().isEmpty()) {
            ner.put("organizations", new JSONArray(e.organizations()));
        }
        e.temporalContext().ifPresent(v -> ner.put("temporalContext", v));
        e.answerTypeHint().ifPresent(v -> ner.put("answerTypeHint", v));
        e.comparisonTypeHint().ifPresent(v -> ner.put("comparisonTypeHint", v));
        for (var entry : plan.slots().entrySet()) {
            ner.put(entry.getKey(), entry.getValue());
        }
        return ner;
    }
}
