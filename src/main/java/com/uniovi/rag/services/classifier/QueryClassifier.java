package com.uniovi.rag.services.classifier;

public interface QueryClassifier {


    QueryType classify(String query);
}
