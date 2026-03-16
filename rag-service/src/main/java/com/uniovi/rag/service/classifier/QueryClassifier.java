package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.Loggable;
import com.uniovi.rag.model.QueryType;

public interface QueryClassifier extends Loggable {

    String classifyWithText(String query);

    QueryType classify(String query);
}
