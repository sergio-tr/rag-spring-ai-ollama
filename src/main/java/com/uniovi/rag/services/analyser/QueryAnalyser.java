package com.uniovi.rag.services.analyser;

import com.uniovi.rag.model.Loggable;
import org.json.JSONObject;

public interface QueryAnalyser extends Loggable {

    JSONObject analyse(String query);
}
