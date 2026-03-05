package com.uniovi.rag.service.query;

import com.uniovi.rag.model.Loggable;
import com.uniovi.rag.model.QueryResponse;

public interface QueryService extends Loggable {

    QueryResponse generateResponse(String question);
}
