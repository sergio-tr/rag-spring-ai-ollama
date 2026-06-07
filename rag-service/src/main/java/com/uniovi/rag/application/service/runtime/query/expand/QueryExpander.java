package com.uniovi.rag.application.service.runtime.query.expand;

import com.uniovi.rag.infrastructure.observability.Loggable;

public interface QueryExpander extends Loggable {

    String expand(String query);
}
