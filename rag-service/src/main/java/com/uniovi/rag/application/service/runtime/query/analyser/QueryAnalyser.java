package com.uniovi.rag.application.service.runtime.query.analyser;

import com.uniovi.rag.infrastructure.observability.Loggable;
import org.json.JSONObject;

public interface QueryAnalyser extends Loggable {

    JSONObject analyse(String query);
}
