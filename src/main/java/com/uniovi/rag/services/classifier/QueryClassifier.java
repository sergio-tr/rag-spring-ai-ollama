package com.uniovi.rag.services.classifier;

import com.uniovi.rag.model.Loggable;

public interface QueryClassifier extends Loggable {

    String classifyWithText(String query);

    QueryType classify(String query);
}
