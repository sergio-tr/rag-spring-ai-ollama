package com.uniovi.rag.service.expand;

import com.uniovi.rag.model.Loggable;

public interface QueryExpander extends Loggable {

    String expand(String query);
}
