package com.uniovi.rag.services.query;

import com.uniovi.rag.model.Loggable;
import com.uniovi.rag.model.QueryResponse;

public interface QueryService extends Loggable {

    QueryResponse generateResponse(String question);
}
