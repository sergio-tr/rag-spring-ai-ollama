package com.uniovi.rag.service.analyser;

import com.uniovi.rag.model.Loggable;
import org.json.JSONObject;

public interface QueryAnalyser extends Loggable {

    JSONObject analyse(String query);
}
