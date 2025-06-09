package com.uniovi.rag.services.query;

import com.uniovi.rag.model.Loggable;

public interface QueryService extends Loggable {

    String generateResponse(String question);
}
