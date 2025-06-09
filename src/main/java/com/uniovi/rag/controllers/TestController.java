package com.uniovi.rag.controllers;

import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final QueryExpander expander;
    private final QueryAnalyser analyser;

    public TestController(QueryExpander expander, QueryAnalyser analyser) {
        this.expander = expander;
        this.analyser = analyser;
    }

    @RequestMapping("/expand")
    public String expand(String question) {
        return expander.expand(question);
    }

    @RequestMapping("/analyse")
    public String analyse(String question) {
        return analyser.analyse(question).toString();
    }
}
