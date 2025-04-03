package com.uniovi.rag.services.expand;

import org.springframework.stereotype.Service;

@Service
public interface QueryExpander {

    String expand(String query);
}
