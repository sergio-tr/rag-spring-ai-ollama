package com.uniovi.rag.services.expand;

import com.uniovi.rag.model.Loggable;
import org.springframework.stereotype.Service;

@Service
public interface QueryExpander extends Loggable {

    String expand(String query);
}
