package com.uniovi.rag.application.service.runtime.query.analyser;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.infrastructure.observability.Loggable;
import org.json.JSONObject;

public interface QueryAnalyser extends Loggable {

    JSONObject analyse(String query);

    default JSONObject analyse(ExecutionContext ctx, String query) {
        return analyse(query);
    }
}
